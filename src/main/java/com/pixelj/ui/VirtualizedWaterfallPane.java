package com.pixelj.ui;

import com.pixelj.internal.cache.ImageCacheCoordinator;
import com.pixelj.internal.loader.PriorityImageLoader;
import com.pixelj.spi.ImageDecoder;
import com.pixelj.util.GroupManager;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private static final double HEADER_HEIGHT = 40;
    private static final int BUFFER_ROWS = 3;

    private final ScrollPane scrollPane;
    private final Pane contentPane;
    private final List<Path> items;
    private final Map<Path, ImageCell> visibleCells;
    private final Map<String, GroupHeaderCell> visibleHeaders;
    private final ImageCacheCoordinator cacheCoordinator;
    private final PriorityImageLoader imageLoader;
    private final GroupManager groupManager;

    private List<DisplayItem> displayItems;
    private Map<Path, ImageDecoder.ImageMetadata> metadataMap;
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
        this.displayItems = new ArrayList<>();
        this.metadataMap = new HashMap<>();
        this.visibleCells = new ConcurrentHashMap<>();
        this.visibleHeaders = new ConcurrentHashMap<>();
        this.groupManager = new GroupManager();
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
        clearAllCells();
        buildDisplayItems();
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
        logger.info("Set {} items in waterfall pane", items.size());
    }

    /**
     * 构建显示项列表（包含分组标题）。
     */
    private void buildDisplayItems() {
        displayItems.clear();

        if (groupManager.getGroupMode() == GroupManager.GroupMode.NONE) {
            for (Path path : items) {
                displayItems.add(new DisplayItem.ImageItem(path));
            }
        } else {
            Map<String, List<Path>> groups = new LinkedHashMap<>();

            for (Path path : items) {
                String key = getGroupKey(path);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
            }

            List<Map.Entry<String, List<Path>>> sortedEntries = new ArrayList<>(groups.entrySet());
            if (groupManager.getGroupMode() == GroupManager.GroupMode.BY_DATE) {
                sortedEntries.sort((a, b) -> b.getKey().compareTo(a.getKey()));
            }

            for (Map.Entry<String, List<Path>> entry : sortedEntries) {
                displayItems.add(new DisplayItem.HeaderItem(
                        entry.getKey(),
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue()
                ));
                for (Path path : entry.getValue()) {
                    displayItems.add(new DisplayItem.ImageItem(path));
                }
            }
        }
    }

    private String getGroupKey(Path path) {
        if (groupManager.getGroupMode() == GroupManager.GroupMode.BY_DATE) {
            ImageDecoder.ImageMetadata meta = metadataMap.get(path);
            long effectiveDate = 0;

            if (meta != null && meta.dateTaken() > 0) {
                effectiveDate = meta.dateTaken();
            }

            if (effectiveDate <= 0) {
                try {
                    effectiveDate = Files.getLastModifiedTime(path).toMillis();
                } catch (Exception e) {
                    // ignore
                }
            }

            if (effectiveDate <= 0) {
                return "未知日期";
            }

            LocalDate date = Instant.ofEpochMilli(effectiveDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
        }
        return "未分组";
    }

    /**
     * 设置分组模式。
     *
     * @param mode 分组模式
     */
    public void setGroupMode(GroupManager.GroupMode mode) {
        groupManager.setGroupMode(mode);
        buildDisplayItems();
        clearAllCells();
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
    }

    /**
     * 设置元数据映射。
     *
     * @param metadataMap 路径到元数据的映射
     */
    public void setMetadataMap(Map<Path, ImageDecoder.ImageMetadata> metadataMap) {
        this.metadataMap = metadataMap;
        buildDisplayItems();
        clearAllCells();
        calculateLayout(scrollPane.getViewportBounds().getWidth());
        repositionCells();
        updateVisibleCells();
    }

    /**
     * 清除所有单元格。
     */
    private void clearAllCells() {
        for (ImageCell cell : visibleCells.values()) {
            cell.setBufferedImage(null);
            contentPane.getChildren().remove(cell);
        }
        visibleCells.clear();
        for (GroupHeaderCell header : visibleHeaders.values()) {
            contentPane.getChildren().remove(header);
        }
        visibleHeaders.clear();
    }

    /**
     * 添加单个图像到列表。
     *
     * @param item 图像路径
     */
    public void addItem(Path item) {
        items.add(item);
        buildDisplayItems();
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
        buildDisplayItems();
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

        int imageCount = 0;
        for (DisplayItem item : displayItems) {
            if (item instanceof DisplayItem.ImageItem) {
                imageCount++;
            }
        }

        int headerCount = 0;
        for (DisplayItem item : displayItems) {
            if (item instanceof DisplayItem.HeaderItem) {
                headerCount++;
            }
        }

        int imageRows = (int) Math.ceil((double) imageCount / columnCount);
        int headerRows = headerCount;

        contentHeight = headerRows * HEADER_HEIGHT + imageRows * (columnWidth + ROW_SPACING) + 50;

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
            int index = getDisplayIndexForPath(path);
            if (index >= 0) {
                Position pos = calculatePosition(index);
                cell.setLayoutX(pos.x);
                cell.setLayoutY(pos.y);
                cell.setPrefSize(columnWidth, columnWidth);
            }
        }

        for (Map.Entry<String, GroupHeaderCell> entry : visibleHeaders.entrySet()) {
            GroupHeaderCell header = entry.getValue();
            int index = getDisplayIndexForHeader(entry.getKey());
            if (index >= 0) {
                Position pos = calculatePosition(index);
                header.setLayoutX(0);
                header.setLayoutY(pos.y);
                header.setPrefSize(columnWidth * columnCount + (columnCount - 1) * COLUMN_SPACING, HEADER_HEIGHT);
            }
        }
    }

    private int getDisplayIndexForPath(Path path) {
        for (int i = 0; i < displayItems.size(); i++) {
            DisplayItem item = displayItems.get(i);
            if (item instanceof DisplayItem.ImageItem img && img.path().equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private int getDisplayIndexForHeader(String headerId) {
        for (int i = 0; i < displayItems.size(); i++) {
            DisplayItem item = displayItems.get(i);
            if (item instanceof DisplayItem.HeaderItem hdr && hdr.id().equals(headerId)) {
                return i;
            }
        }
        return -1;
    }

    private Position calculatePosition(int displayIndex) {
        int imageIndex = 0;
        int headerIndex = 0;
        int col = 0;
        int row = 0;

        for (int i = 0; i <= displayIndex; i++) {
            DisplayItem item = displayItems.get(i);
            if (item instanceof DisplayItem.HeaderItem) {
                headerIndex++;
                row++;
            } else {
                imageIndex++;
                int currentCol = (imageIndex - 1) % columnCount;
                int currentRow = headerIndex + (imageIndex - 1) / columnCount;
                col = currentCol;
                row = currentRow;
            }
        }

        double x = col * (columnWidth + COLUMN_SPACING);
        double y = row * (columnWidth + ROW_SPACING);
        return new Position(x, y);
    }

    private record Position(double x, double y) {
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
        Set<String> toLoadHeaders = new HashSet<>();
        Set<String> toRecycleHeaders = new HashSet<>(visibleHeaders.keySet());

        for (int i = 0; i < displayItems.size(); i++) {
            DisplayItem item = displayItems.get(i);
            Position pos = calculatePosition(i);

            if (item instanceof DisplayItem.HeaderItem hdr) {
                double itemBottom = pos.y + HEADER_HEIGHT;
                if (itemBottom >= viewTop && pos.y <= viewBottom) {
                    toRecycleHeaders.remove(hdr.id());
                    if (!visibleHeaders.containsKey(hdr.id())) {
                        toLoadHeaders.add(hdr.id());
                    }
                }
            } else if (item instanceof DisplayItem.ImageItem img) {
                double itemBottom = pos.y + columnWidth;
                if (itemBottom >= viewTop && pos.y <= viewBottom) {
                    toRecycle.remove(img.path());
                    if (!visibleCells.containsKey(img.path())) {
                        toLoad.add(img.path());
                    }
                }
            }
        }

        for (String headerId : toRecycleHeaders) {
            GroupHeaderCell header = visibleHeaders.remove(headerId);
            if (header != null) {
                contentPane.getChildren().remove(header);
            }
        }

        for (Path path : toRecycle) {
            ImageCell cell = visibleCells.remove(path);
            if (cell != null) {
                cell.setBufferedImage(null);
                contentPane.getChildren().remove(cell);
            }
        }

        for (String headerId : toLoadHeaders) {
            GroupHeaderCell header = getOrCreateHeader(headerId);
            int index = getDisplayIndexForHeader(headerId);
            if (index >= 0) {
                Position pos = calculatePosition(index);
                header.setLayoutX(0);
                header.setLayoutY(pos.y);
                header.setPrefSize(columnWidth * columnCount + (columnCount - 1) * COLUMN_SPACING, HEADER_HEIGHT);
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

            int index = getDisplayIndexForPath(p);
            if (index >= 0) {
                Position pos = calculatePosition(index);
                cell.setLayoutX(pos.x);
                cell.setLayoutY(pos.y);
                cell.setPrefSize(columnWidth, columnWidth);
            }

            contentPane.getChildren().add(cell);
            return cell;
        });
    }

    /**
     * 获取或创建分组标题单元格。
     *
     * @param headerId 标题ID
     * @return 分组标题单元格
     */
    private GroupHeaderCell getOrCreateHeader(String headerId) {
        return visibleHeaders.computeIfAbsent(headerId, id -> {
            GroupHeaderCell header = new GroupHeaderCell();

            for (DisplayItem item : displayItems) {
                if (item instanceof DisplayItem.HeaderItem hdr && hdr.id().equals(id)) {
                    header.setTitle(hdr.title());
                    header.setCount(hdr.count());
                    header.setGroupId(id);
                    break;
                }
            }

            contentPane.getChildren().add(header);
            return header;
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