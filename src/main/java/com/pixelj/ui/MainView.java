package com.pixelj.ui;

import com.pixelj.internal.cache.ImageCacheCoordinator;
import com.pixelj.internal.db.MetadataIndex;
import com.pixelj.internal.fs.FileScanner;
import com.pixelj.internal.fs.FileWatcher;
import com.pixelj.internal.loader.PriorityImageLoader;
import com.pixelj.util.HistoryManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * PixelJ 主视图。
 * 管理应用程序的主 UI 界面，包括工具栏、内容区域和状态栏。
 * 协调图像缓存、加载和文件系统监控等核心功能。
 */
public class MainView {

    private static final Logger logger = LoggerFactory.getLogger(MainView.class);

    private final Stage stage;
    private final ImageCacheCoordinator cacheCoordinator;
    private final PriorityImageLoader imageLoader;
    private final MetadataIndex metadataIndex;
    private final FileScanner fileScanner;

    private VirtualizedWaterfallPane waterfallPane;
    private Label statusLabel;
    private Label imageCountLabel;
    private ProgressBar progressBar;
    private Path currentDirectory;

    private FileWatcher fileWatcher;

    public MainView(Stage stage) {
        this.stage = stage;
        this.cacheCoordinator = new ImageCacheCoordinator(getCacheRoot());
        this.imageLoader = new PriorityImageLoader(cacheCoordinator,
                Runtime.getRuntime().availableProcessors());
        this.fileScanner = new FileScanner();

        try {
            this.metadataIndex = new MetadataIndex();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize metadata index", e);
        }
    }

    /**
     * 创建主场景。
     *
     * @return 配置好的 Scene 对象
     */
    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setTop(createToolbar());
        root.setCenter(createContent());
        root.setBottom(createStatusBar());
        root.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        Scene scene = new Scene(root, 1280, 800);

        stage.setTitle("PixelJ - Photo Browser");
        stage.setOnCloseRequest(e -> shutdown());

        return scene;
    }

    /**
     * 获取缓存根目录路径。
     *
     * @return 缓存根目录
     */
    private Path getCacheRoot() {
        return Paths.get(System.getProperty("user.home"), ".pixelj", "cache");
    }

    /**
     * 创建工具栏。
     *
     * @return 工具栏
     */
    private ToolBar createToolbar() {
        Button openFolderBtn = new Button("打开文件夹");
        openFolderBtn.setOnAction(e -> openFolder());

        MenuButton historyMenuBtn = new MenuButton("历史记录");
        historyMenuBtn.setStyle("-fx-background-color: transparent;");
        updateHistoryMenu(historyMenuBtn);

        Button refreshBtn = new Button("刷新");
        refreshBtn.setOnAction(e -> refreshCurrentFolder());

        Separator sep = new Separator();
        sep.setPrefWidth(20);

        Label titleLabel = new Label("PixelJ");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox titleBox = new HBox(titleLabel);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ToolBar toolBar = new ToolBar(openFolderBtn, historyMenuBtn, refreshBtn, sep, titleBox, spacer);
        toolBar.setPadding(new Insets(8, 8, 8, 8));

        return toolBar;
    }

    /**
     * 更新历史记录菜单。
     *
     * @param historyMenuBtn 历史菜单按钮
     */
    private void updateHistoryMenu(MenuButton historyMenuBtn) {
        historyMenuBtn.getItems().clear();

        HistoryManager historyManager = HistoryManager.getInstance();
        List<HistoryManager.HistoryItem> history = historyManager.getHistory();

        if (history.isEmpty()) {
            MenuItem emptyItem = new MenuItem("暂无历史记录");
            emptyItem.setDisable(true);
            historyMenuBtn.getItems().add(emptyItem);
        } else {
            for (HistoryManager.HistoryItem item : history) {
                MenuItem menuItem = new MenuItem(item.getFileName());
                menuItem.setOnAction(e -> loadDirectory(item.getPath()));
                historyMenuBtn.getItems().add(menuItem);
            }

            historyMenuBtn.getItems().add(new SeparatorMenuItem());

            MenuItem clearItem = new MenuItem("清除历史");
            clearItem.setOnAction(e -> {
                historyManager.clearHistory();
                updateHistoryMenu(historyMenuBtn);
            });
            historyMenuBtn.getItems().add(clearItem);
        }
    }

    /**
     * 创建主内容区域。
     *
     * @return 内容区域
     */
    private Region createContent() {
        StackPane content = new StackPane();

        Label placeholder = new Label("Open a folder to browse photos");
        placeholder.setStyle("-fx-font-size: 24px; -fx-text-fill: #666;");

        VBox placeholderBox = new VBox(placeholder);
        placeholderBox.setAlignment(Pos.CENTER);

        waterfallPane = new VirtualizedWaterfallPane(cacheCoordinator, imageLoader);
        waterfallPane.setOnItemClicked(this::onImageClicked);

        StackPane.setAlignment(waterfallPane, Pos.TOP_LEFT);

        content.getChildren().addAll(placeholderBox, waterfallPane);

        return content;
    }

    /**
     * 创建状态栏。
     *
     * @return 状态栏 HBox
     */
    private HBox createStatusBar() {
        statusLabel = new Label("Ready");
        statusLabel.setPadding(new Insets(4, 8, 4, 8));

        imageCountLabel = new Label("0 images");
        imageCountLabel.setPadding(new Insets(4, 8, 4, 8));

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(150);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox statusBar = new HBox(statusLabel, new Separator(), imageCountLabel, spacer, progressBar);
        statusBar.setStyle("-fx-background-color: #f0f0f0; -fx-border-color: #ddd;");
        statusBar.setPadding(new Insets(4, 8, 4, 8));

        return statusBar;
    }

    /**
     * 打开文件夹选择对话框。
     */
    private void openFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Photo Folder");

        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            loadDirectory(selectedDir.toPath());
        }
    }

    /**
     * 加载指定目录中的图像。
     *
     * @param directory 目录路径
     */
    private void loadDirectory(Path directory) {
        currentDirectory = directory;
        stage.setTitle("PixelJ - " + directory.getFileName());

        HistoryManager.getInstance().addHistory(directory);

        statusLabel.setText("Scanning...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<Path> imageFiles = fileScanner.scanDirectory(directory);

                Platform.runLater(() -> {
                    statusLabel.setText("Loading...");
                    waterfallPane.setItems(imageFiles);

                    imageCountLabel.setText(imageFiles.size() + " images");
                    progressBar.setVisible(false);
                    statusLabel.setText("Ready");
                });

                try {
                    if (fileWatcher != null) {
                        fileWatcher.stop();
                    }
                    fileWatcher = new FileWatcher(directory);
                    fileWatcher.setOnFileAdded(path -> Platform.runLater(() -> {
                        if (fileScanner.isImageFile(path)) {
                            waterfallPane.addItem(path);
                            imageCountLabel.setText(waterfallPane.getItems().size() + " images");
                        }
                    }));
                    fileWatcher.setOnFileRemoved(path -> Platform.runLater(() -> {
                        waterfallPane.removeItem(path);
                        imageCountLabel.setText(waterfallPane.getItems().size() + " images");
                    }));
                    fileWatcher.start();
                } catch (Exception e) {
                    logger.error("Failed to start file watcher", e);
                }

            } catch (Exception e) {
                logger.error("Failed to load directory", e);
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading directory");
                    progressBar.setVisible(false);
                });
            }
        });
    }

    /**
     * 刷新当前文件夹。
     */
    private void refreshCurrentFolder() {
        if (currentDirectory != null) {
            loadDirectory(currentDirectory);
        }
    }

    /**
     * 图像点击事件处理。
     *
     * @param path 被点击的图像路径
     */
    private void onImageClicked(Path path) {
        logger.debug("Image clicked: {}", path);
        statusLabel.setText("Selected: " + path.getFileName());
    }

    /**
     * 关闭应用程序并释放资源。
     */
    private void shutdown() {
        if (fileWatcher != null) {
            fileWatcher.stop();
        }
        imageLoader.shutdown();
        cacheCoordinator.shutdown();
        metadataIndex.close();
        logger.info("Application shutdown complete");
    }

    /**
     * 获取当前显示的图像列表。
     *
     * @return 图像路径列表
     */
    public List<Path> getItems() {
        return waterfallPane != null ? waterfallPane.getItems() : List.of();
    }
}