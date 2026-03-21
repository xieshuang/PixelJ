package com.pixelj.ui;

import com.pixelj.internal.cache.ImageCacheCoordinator;
import com.pixelj.internal.loader.PriorityImageLoader;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 虚拟化瀑布流面板。
 * 支持大量图像的高效渲染，只绘制可见区域的图像单元格。
 * 通过滚动位置计算实现 60fps 的流畅滚动体验。
 */
public class VirtualizedWaterfallPane extends Region {

    private static final Logger logger = LoggerFactory.getLogger(VirtualizedWaterfallPane.class);

    private static final double COLUMN_WIDTH = 200;
    private static final double COLUMN_SPACING = 8;
    private static final double ROW_SPACING = 8;
    private static final int BUFFER_ROWS = 3;

    private final ScrollPane scrollPane;
    private final Pane contentPane;
    private final List<Path> items;
    private final Map<Path, ImageCell> visibleCells;
    private final ImageCacheCoordinator cacheCoordinator;
    private final PriorityImageLoader imageLoader;

    private int columnCount;
    private double contentHeight;
    private double columnWidth;
    private double lastScrollY = -1;
    private boolean needsUpdate = false;

    private Consumer<Path> onItemClicked;
    private Path selectedPath;

    public VirtualizedWaterfallPane(ImageCacheCoordinator cacheCoordinator, PriorityImageLoader imageLoader) {
        this.cacheCoordinator = cacheCoordinator;
        this.imageLoader = imageLoader;
        this.items = new ArrayList<>();
        this.visibleCells = new ConcurrentHashMap<>();
        this.columnCount = 4;

        this.contentPane = new Pane();
        this.contentPane.setStyle("-fx-background-color: #1a1a1a;");

        this.scrollPane = new ScrollPane(contentPane);
        this.scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        this.scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        this.scrollPane.setFitToWidth(true);
        this.scrollPane.setPannable(false);
        this.scrollPane.setStyle("-fx-background: #1a1a1a;");

        getChildren().add(scrollPane);

        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - lastScrollY) > 0.001) {
                lastScrollY = newVal.doubleValue();
                needsUpdate = true;
            }
        });

        AnimationTimer scrollTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (needsUpdate) {
                    updateVisibleCells();
                    needsUpdate = false;
                }
            }
        };
        scrollTimer.start();

        scrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            if (newBounds.getWidth() > 0) {
                calculateLayout(newBounds.getWidth());
                repositionCells();
                updateVisibleCells();
            }
        });
    }

    /**
     * 设置要显示的图像列表。
     * 替换现有所有图像并重新布局。
     *
     * @param newItems 新的图像路径列表
     */
    public void setItems(List<Path> newItems) {
        items.clear();
        items.addAll(newItems);
        for (ImageCell cell : visibleCells.values()) {
            cell.setBufferedImage(null);
            contentPane.getChildren().remove(cell);
        }
        visibleCells.clear();
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
        logger.info("Set {} items in waterfall pane", items.size());
    }

    /**
     * 添加单个图像到列表。
     *
     * @param item 图像路径
     */
    public void addItem(Path item) {
        items.add(item);
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
    }

    /**
     * 从列表中移除指定图像。
     *
     * @param item 图像路径
     */
    public void removeItem(Path item) {
        items.remove(item);
        ImageCell cell = visibleCells.remove(item);
        if (cell != null) {
            cell.setBufferedImage(null);
            contentPane.getChildren().remove(cell);
        }
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
    }

    /**
     * 计算瀑布流布局参数。
     *
     * @param viewportWidth 视口宽度
     */
    private void calculateLayout(double viewportWidth) {
        if (viewportWidth <= 0) return;

        columnCount = Math.max(1, (int) ((viewportWidth + COLUMN_SPACING) / (COLUMN_WIDTH + COLUMN_SPACING)));
        columnWidth = (viewportWidth - (columnCount - 1) * COLUMN_SPACING) / columnCount;

        int rows = (int) Math.ceil((double) items.size() / columnCount);
        contentHeight = rows * (columnWidth + ROW_SPACING) + 50;

        contentPane.setPrefHeight(contentHeight);
        contentPane.setPrefWidth(viewportWidth);
    }

    /**
     * 重新定位所有可见单元格的位置。
     */
    private void repositionCells() {
        for (Map.Entry<Path, ImageCell> entry : visibleCells.entrySet()) {
            Path path = entry.getKey();
            ImageCell cell = entry.getValue();
            int index = items.indexOf(path);
            if (index >= 0) {
                int col = index % columnCount;
                int row = index / columnCount;
                double x = col * (columnWidth + COLUMN_SPACING);
                double y = row * (columnWidth + ROW_SPACING);
                cell.setLayoutX(x);
                cell.setLayoutY(y);
                cell.setPrefSize(columnWidth, columnWidth);
            }
        }
    }

    /**
     * 更新可见单元格。
     */
    private void updateVisibleCells() {
        Bounds viewportBounds = scrollPane.getViewportBounds();
        if (viewportBounds.getHeight() <= 0) return;

        double viewTop = scrollPane.getVvalue() * (contentHeight - viewportBounds.getHeight());
        double viewBottom = viewTop + viewportBounds.getHeight() + BUFFER_ROWS * (columnWidth + ROW_SPACING);

        Set<Path> toLoad = new HashSet<>();
        Set<Path> toRecycle = new HashSet<>(visibleCells.keySet());

        for (int i = 0; i < items.size(); i++) {
            Path item = items.get(i);
            int col = i % columnCount;
            int row = i / columnCount;

            double itemX = col * (columnWidth + COLUMN_SPACING);
            double itemY = row * (columnWidth + ROW_SPACING);
            double itemBottom = itemY + columnWidth;

            if (itemBottom >= viewTop && itemY <= viewBottom) {
                toRecycle.remove(item);
                if (!visibleCells.containsKey(item)) {
                    toLoad.add(item);
                }
            }
        }

        for (Path path : toRecycle) {
            ImageCell cell = visibleCells.remove(path);
            if (cell != null) {
                cell.setBufferedImage(null);
                contentPane.getChildren().remove(cell);
            }
        }

        for (Path path : toLoad) {
            ImageCell cell = getOrCreateCell(path);
            loadImage(cell, path);
        }
    }

    /**
     * 获取或创建指定路径的图像单元格。
     *
     * @param path 图像路径
     * @return 图像单元格
     */
    private ImageCell getOrCreateCell(Path path) {
        return visibleCells.computeIfAbsent(path, p -> {
            ImageCell cell = new ImageCell();
            cell.setOnClick(e -> {
                Path previousSelected = selectedPath;
                selectedPath = p;

                if (previousSelected != null && !previousSelected.equals(p)) {
                    ImageCell prevCell = visibleCells.get(previousSelected);
                    if (prevCell != null) {
                        prevCell.setSelected(false);
                    }
                }

                cell.setSelected(true);

                if (onItemClicked != null) {
                    onItemClicked.accept(p);
                }
            });

            cell.setOnDoubleClick(e -> {
                logger.debug("Double clicked on: {}", p);
                ImageViewerDialog viewer = new ImageViewerDialog(p);
                viewer.show();
            });

            int index = items.indexOf(p);
            int col = index % columnCount;
            int row = index / columnCount;

            double x = col * (columnWidth + COLUMN_SPACING);
            double y = row * (columnWidth + ROW_SPACING);

            cell.setLayoutX(x);
            cell.setLayoutY(y);
            cell.setPrefSize(columnWidth, columnWidth);

            contentPane.getChildren().add(cell);
            return cell;
        });
    }

    /**
     * 加载图像到指定单元格。
     *
     * @param cell 目标单元格
     * @param path 图像路径
     */
    private void loadImage(ImageCell cell, Path path) {
        imageLoader.submit(path, PriorityImageLoader.Priority.HIGH)
                .thenAccept(image -> {
                    if (image != null) {
                        Platform.runLater(() -> cell.setBufferedImage(image));
                    }
                });
    }

    /**
     * 设置图像点击事件处理回调。
     *
     * @param callback 点击事件回调
     */
    public void setOnItemClicked(Consumer<Path> callback) {
        this.onItemClicked = callback;
    }

    /**
     * 获取当前显示的图像列表。
     *
     * @return 图像路径列表
     */
    public List<Path> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * 获取内部的 ScrollPane 实例。
     *
     * @return ScrollPane
     */
    public ScrollPane getScrollPane() {
        return scrollPane;
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        if (w > 0 && h > 0) {
            scrollPane.setLayoutX(0);
            scrollPane.setLayoutY(0);
            scrollPane.resize(w, h);
        }
    }
}