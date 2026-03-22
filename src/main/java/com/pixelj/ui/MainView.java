package com.pixelj.ui;

import com.pixelj.internal.cache.ImageCacheCoordinator;
import com.pixelj.internal.db.MetadataIndex;
import com.pixelj.internal.fs.FileScanner;
import com.pixelj.internal.fs.FileWatcher;
import com.pixelj.internal.loader.MetadataLoader;
import com.pixelj.internal.loader.PriorityImageLoader;
import com.pixelj.util.GroupManager;
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
 * 管理应用程序的主 UI 界面，采用现代化深色主题。
 */
public class MainView {

    private static final Logger logger = LoggerFactory.getLogger(MainView.class);

    private static final String COLOR_BG = "#1e1e1e";
    private static final String COLOR_SURFACE = "#252526";
    private static final String COLOR_BORDER = "#3c3c3c";
    private static final String COLOR_TEXT = "#cccccc";
    private static final String COLOR_TEXT_DIM = "#808080";
    private static final String COLOR_ACCENT = "#0c60ee";
    private static final String COLOR_BUTTON_BG = "#3c3c3c";
    private static final String COLOR_BUTTON_HOVER = "#4c4c4c";
    private static final String COLOR_SEPARATOR = "#474747";

    private final Stage stage;
    private final ImageCacheCoordinator cacheCoordinator;
    private final PriorityImageLoader imageLoader;
    private final MetadataIndex metadataIndex;
    private final MetadataLoader metadataLoader;
    private final FileScanner fileScanner;

    private VirtualizedWaterfallPane waterfallPane;
    private Label statusLabel;
    private Label imageCountLabel;
    private ProgressBar progressBar;
    private Path currentDirectory;
    private Label directoryLabel;
    private FileWatcher fileWatcher;

    public MainView(Stage stage) {
        this.stage = stage;
        this.cacheCoordinator = new ImageCacheCoordinator(getCacheRoot());
        this.imageLoader = new PriorityImageLoader(cacheCoordinator,
                Runtime.getRuntime().availableProcessors());
        this.fileScanner = new FileScanner();

        try {
            this.metadataIndex = new MetadataIndex();
            this.metadataLoader = new MetadataLoader(metadataIndex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize metadata index", e);
        }
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + COLOR_BG + ";");

        VBox titleBar = createTitleBar();
        root.setTop(titleBar);

        HBox toolBar = createToolBar();
        root.setTop(toolBar);

        StackPane content = createContent();
        root.setCenter(content);

        HBox statusBar = createStatusBar();
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1280, 800);
        scene.setFill(null);

        String menuCss = 
            ".context-menu {" +
            "    -fx-background-color: #2d2d2d;" +
            "    -fx-border-color: #3c3c3c;" +
            "    -fx-border-width: 1px;" +
            "    -fx-padding: 4px;" +
            "}" +
            ".menu-item {" +
            "    -fx-background-color: transparent;" +
            "    -fx-padding: 6px 30px 6px 12px;" +
            "}" +
            ".menu-item:hover {" +
            "    -fx-background-color: #3a3a3a;" +
            "}" +
            ".menu-item:focused {" +
            "    -fx-background-color: #3a3a3a;" +
            "}" +
            ".menu-item .label {" +
            "    -fx-text-fill: #cccccc;" +
            "}" +
            ".menu-item:hover .label {" +
            "    -fx-text-fill: #ffffff;" +
            "}" +
            ".menu-item:disabled .label {" +
            "    -fx-text-fill: #808080;" +
            "}" +
            ".separator-menu-item .line {" +
            "    -fx-background-color: #3c3c3c;" +
            "}";
        scene.getStylesheets().add("data:text/css," + menuCss.replaceAll("\\s+", " "));

        stage.setTitle("PixelJ");
        stage.setOnCloseRequest(e -> shutdown());

        return scene;
    }

    private Path getCacheRoot() {
        return Paths.get(System.getProperty("user.home"), ".pixelj", "cache");
    }

    private VBox createTitleBar() {
        VBox titleBar = new VBox();
        titleBar.setStyle(
            "-fx-background-color: " + COLOR_SURFACE + ";" +
            "-fx-padding: 0;"
        );

        MenuBar menuBar = new MenuBar();
        menuBar.setStyle(
            "-fx-background-color: " + COLOR_SURFACE + ";" +
            "-fx-border-color: " + COLOR_BORDER + ";" +
            "-fx-border-width: 0 0 1 0;"
        );

        Menu fileMenu = new Menu("文件");
        fileMenu.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");

        MenuItem openItem = new MenuItem("打开文件夹...");
        openItem.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        openItem.setOnAction(e -> openFolder());

        MenuItem exitItem = new MenuItem("退出");
        exitItem.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        exitItem.setOnAction(e -> shutdown());

        fileMenu.getItems().addAll(openItem, new SeparatorMenuItem(), exitItem);

        Menu viewMenu = new Menu("视图");
        viewMenu.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");

        MenuItem refreshItem = new MenuItem("刷新");
        refreshItem.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        refreshItem.setOnAction(e -> refreshCurrentFolder());

        viewMenu.getItems().add(refreshItem);

        Menu helpMenu = new Menu("帮助");
        helpMenu.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");

        MenuItem aboutItem = new MenuItem("关于 PixelJ");
        aboutItem.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        aboutItem.setOnAction(e -> showAboutDialog());

        helpMenu.getItems().add(aboutItem);

        menuBar.getMenus().addAll(fileMenu, viewMenu, helpMenu);

        HBox topRow = new HBox();
        topRow.setStyle(
            "-fx-background-color: " + COLOR_SURFACE + ";" +
            "-fx-padding: 8 12 8 12;" +
            "-fx-alignment: center-left;"
        );
        topRow.setPrefHeight(48);

        Label logoLabel = new Label("PixelJ");
        logoLabel.setStyle(
            "-fx-font-size: 18px;" +
            "-fx-font-weight: bold;" +
            "-fx-text-fill: " + COLOR_TEXT + ";"
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        directoryLabel = new Label("未加载文件夹");
        directoryLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";"
        );

        topRow.getChildren().addAll(logoLabel, spacer, directoryLabel);

        titleBar.getChildren().addAll(menuBar, topRow);

        return titleBar;
    }

    private HBox createToolBar() {
        HBox toolBar = new HBox();
        toolBar.setStyle(
            "-fx-background-color: " + COLOR_SURFACE + ";" +
            "-fx-border-color: " + COLOR_BORDER + ";" +
            "-fx-border-width: 0 0 1 0;" +
            "-fx-padding: 8 12 8 12;" +
            "-fx-alignment: center-left;"
        );
        toolBar.setPrefHeight(48);
        toolBar.setSpacing(8);

        Button openFolderBtn = createToolButton("📁 打开文件夹");
        openFolderBtn.setOnAction(e -> openFolder());

        MenuButton historyMenuBtn = new MenuButton("📋 历史记录");
        historyMenuBtn.setStyle(getMenuButtonStyle());
        updateHistoryMenu(historyMenuBtn);

        Button refreshBtn = createToolButton("🔄 刷新");
        refreshBtn.setOnAction(e -> refreshCurrentFolder());

        Separator sep = new Separator();
        sep.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sep.setPrefWidth(1);
        sep.setPrefHeight(24);
        sep.setStyle("-fx-background-color: " + COLOR_SEPARATOR + ";");

        ToggleGroup viewToggleGroup = new ToggleGroup();

        RadioButton gridViewBtn = new RadioButton("瀑布流");
        gridViewBtn.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        gridViewBtn.setToggleGroup(viewToggleGroup);
        gridViewBtn.setSelected(true);
        gridViewBtn.setOnAction(e -> {
            if (waterfallPane != null) {
                waterfallPane.setGroupMode(GroupManager.GroupMode.NONE);
            }
        });

        RadioButton dateGroupBtn = new RadioButton("按日期分组");
        dateGroupBtn.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
        dateGroupBtn.setToggleGroup(viewToggleGroup);
        dateGroupBtn.setOnAction(e -> {
            if (waterfallPane != null) {
                waterfallPane.setGroupMode(GroupManager.GroupMode.BY_DATE);
            }
        });

        viewToggleGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == gridViewBtn) {
                gridViewBtn.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
                dateGroupBtn.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
            } else if (newVal == dateGroupBtn) {
                gridViewBtn.setStyle("-fx-text-fill: " + COLOR_TEXT + ";");
                dateGroupBtn.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");
            }
        });

        gridViewBtn.setStyle("-fx-text-fill: #ffffff; -fx-font-weight: bold;");

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        toolBar.getChildren().addAll(
            openFolderBtn, historyMenuBtn, refreshBtn, sep,
            gridViewBtn, dateGroupBtn,
            rightSpacer
        );

        return toolBar;
    }

    private Button createToolButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
            "-fx-background-color: " + COLOR_BUTTON_BG + ";" +
            "-fx-text-fill: " + COLOR_TEXT + ";" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;"
        );
        btn.setOnMouseEntered(e -> btn.setStyle(
            "-fx-background-color: " + COLOR_BUTTON_HOVER + ";" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;"
        ));
        btn.setOnMouseExited(e -> btn.setStyle(
            "-fx-background-color: " + COLOR_BUTTON_BG + ";" +
            "-fx-text-fill: " + COLOR_TEXT + ";" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;" +
            "-fx-border-radius: 4;"
        ));
        return btn;
    }

    private String getMenuButtonStyle() {
        return String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_BUTTON_BG, COLOR_TEXT
        );
    }

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

        historyMenuBtn.setOnMouseEntered(e -> historyMenuBtn.setStyle(
            "-fx-background-color: " + COLOR_BUTTON_HOVER + ";" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;"
        ));
        historyMenuBtn.setOnMouseExited(e -> historyMenuBtn.setStyle(getMenuButtonStyle()));
    }

    private StackPane createContent() {
        StackPane content = new StackPane();
        content.setStyle("-fx-background-color: " + COLOR_BG + ";");

        Label placeholder = new Label("📂  打开文件夹以浏览照片");
        placeholder.setStyle(
            "-fx-font-size: 18px;" +
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";" +
            "-fx-padding: 20;"
        );

        VBox placeholderBox = new VBox(placeholder);
        placeholderBox.setAlignment(Pos.CENTER);
        placeholderBox.setStyle("-fx-background-color: " + COLOR_BG + ";");

        waterfallPane = new VirtualizedWaterfallPane(cacheCoordinator, imageLoader);
        waterfallPane.setOnItemClicked(this::onImageClicked);

        StackPane.setAlignment(waterfallPane, Pos.TOP_LEFT);

        content.getChildren().addAll(placeholderBox, waterfallPane);

        return content;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setStyle(
            "-fx-background-color: " + COLOR_SURFACE + ";" +
            "-fx-border-color: " + COLOR_BORDER + ";" +
            "-fx-border-width: 1 0 0 0;" +
            "-fx-padding: 0;"
        );
        statusBar.setPrefHeight(28);
        statusBar.setSpacing(0);

        statusLabel = new Label("就绪");
        statusLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";" +
            "-fx-padding: 0 12 0 12;"
        );
        statusLabel.setPrefHeight(28);

        Separator sep1 = new Separator();
        sep1.setOrientation(javafx.geometry.Orientation.VERTICAL);
        sep1.setPrefWidth(1);
        sep1.setPrefHeight(16);
        sep1.setStyle("-fx-background-color: " + COLOR_SEPARATOR + ";");
        sep1.setLayoutY(6);

        imageCountLabel = new Label("0 张照片");
        imageCountLabel.setStyle(
            "-fx-font-size: 12px;" +
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";" +
            "-fx-padding: 0 12 0 12;"
        );
        imageCountLabel.setPrefHeight(28);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        progressBar = new ProgressBar(0);
        progressBar.setStyle(
            "-fx-control-inner-background: " + COLOR_BUTTON_BG + ";" +
            "-fx-accent: " + COLOR_ACCENT + ";"
        );
        progressBar.setPrefWidth(120);
        progressBar.setVisible(false);
        progressBar.setPrefHeight(6);
        progressBar.setLayoutY(11);

        statusBar.getChildren().addAll(statusLabel, sep1, imageCountLabel, spacer, progressBar);

        VBox.setMargin(statusBar, new Insets(0));

        return statusBar;
    }

    private void openFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择照片文件夹");

        File selectedDir = chooser.showDialog(stage);
        if (selectedDir != null) {
            loadDirectory(selectedDir.toPath());
        }
    }

    private void loadDirectory(Path directory) {
        currentDirectory = directory;
        stage.setTitle("PixelJ - " + directory.getFileName());
        directoryLabel.setText(directory.toString());

        HistoryManager.getInstance().addHistory(directory);

        statusLabel.setText("正在扫描...");
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                List<Path> imageFiles = fileScanner.scanDirectory(directory);

                Platform.runLater(() -> {
                    statusLabel.setText("正在加载...");
                    waterfallPane.setItems(imageFiles);

                    imageCountLabel.setText(imageFiles.size() + " 张照片");
                    progressBar.setVisible(false);
                    statusLabel.setText("就绪");
                });

                metadataLoader.preloadAsync(directory, imageFiles, () -> {
                    Platform.runLater(() -> {
                        var metaMap = metadataLoader.getMetadataMap(directory);
                        waterfallPane.setMetadataMap(metaMap);
                    });
                });

                try {
                    if (fileWatcher != null) {
                        fileWatcher.stop();
                    }
                    fileWatcher = new FileWatcher(directory);
                    fileWatcher.setOnFileAdded(path -> Platform.runLater(() -> {
                        if (fileScanner.isImageFile(path)) {
                            waterfallPane.addItem(path);
                            imageCountLabel.setText(waterfallPane.getItems().size() + " 张照片");
                        }
                    }));
                    fileWatcher.setOnFileRemoved(path -> Platform.runLater(() -> {
                        waterfallPane.removeItem(path);
                        imageCountLabel.setText(waterfallPane.getItems().size() + " 张照片");
                    }));
                    fileWatcher.start();
                } catch (Exception e) {
                    logger.error("Failed to start file watcher", e);
                }

            } catch (Exception e) {
                logger.error("Failed to load directory", e);
                Platform.runLater(() -> {
                    statusLabel.setText("加载目录失败");
                    progressBar.setVisible(false);
                });
            }
        });
    }

    private void refreshCurrentFolder() {
        if (currentDirectory != null) {
            loadDirectory(currentDirectory);
        }
    }

    private void onImageClicked(Path path) {
        logger.debug("Image clicked: {}", path);
        statusLabel.setText("已选择: " + path.getFileName());
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于 PixelJ");
        alert.setHeaderText("PixelJ");
        alert.setContentText(
            "高性能个人电脑照片浏览器\n\n" +
            "版本: 1.0.0\n" +
            "基于 Java 17 + JavaFX 21"
        );
        alert.showAndWait();
    }

    private void shutdown() {
        new Thread(() -> {
            try {
                if (fileWatcher != null) {
                    fileWatcher.stop();
                }
                imageLoader.shutdown();
                cacheCoordinator.shutdown();
                metadataIndex.close();
                logger.info("Application shutdown complete");
            } catch (Exception e) {
                logger.error("Error during shutdown", e);
            }
        }, "pixelj-shutdown").start();
    }

    public List<Path> getItems() {
        return waterfallPane != null ? waterfallPane.getItems() : List.of();
    }
}
