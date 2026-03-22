package com.pixelj.ui;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * 原图查看器对话框。
 * 以原始尺寸显示图片，支持缩放、平移、上一张/下一张切换。
 * 使用现代化深色主题界面。
 */
public class ImageViewerDialog {

    private static final Logger logger = LoggerFactory.getLogger(ImageViewerDialog.class);

    private static final String COLOR_BG_DARK = "#1e1e1e";
    private static final String COLOR_TITLE_BAR = "#2d2d2d";
    private static final String COLOR_TITLE_BAR_HOVER = "#383838";
    private static final String COLOR_BUTTON_BG = "#3a3a3a";
    private static final String COLOR_BUTTON_HOVER = "#505050";
    private static final String COLOR_TEXT = "#cccccc";
    private static final String COLOR_TEXT_DIM = "#888888";
    private static final String COLOR_CLOSE_HOVER = "#c42b1c";

    private final Stage dialog;
    private ImageView imageView;
    private ScrollPane scrollPane;
    private final List<Path> imagePaths;
    private Label indexLabel;
    private Label imageInfoLabel;
    private Button prevBtn;
    private Button nextBtn;
    private Label pathLabel = new Label("");
    
    private int currentIndex;
    private double targetFitWidth = 0;
    private double targetFitHeight = 0;
    private double zoomFactor = 1.0;
    private Path currentImagePath;
    private double dragX;
    private double dragY;

    public ImageViewerDialog(List<Path> imagePaths, int initialIndex) {
        this.imagePaths = imagePaths;
        this.currentIndex = initialIndex;
        this.currentImagePath = imagePaths.get(initialIndex);
        
        this.dialog = new Stage(StageStyle.TRANSPARENT);
        this.dialog.initModality(Modality.APPLICATION_MODAL);
        this.dialog.setResizable(true);
        this.dialog.setWidth(900);
        this.dialog.setHeight(700);
        this.dialog.setMinWidth(400);
        this.dialog.setMinHeight(400);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: " + COLOR_BG_DARK + ";");

        HBox titleBar = createTitleBar();
        root.getChildren().add(titleBar);

        StackPane contentArea = createContentArea();
        VBox.setVgrow(contentArea, Priority.ALWAYS);
        root.getChildren().add(contentArea);

        HBox bottomBar = createBottomBar();
        root.getChildren().add(bottomBar);

        Scene scene = new Scene(root);
        scene.setFill(null);
        applyStyles(scene);
        dialog.setScene(scene);

        dialog.setOnCloseRequest(e -> logger.debug("Image viewer closed"));

        scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            } else if (e.getCode() == KeyCode.LEFT) {
                showPrevious();
            } else if (e.getCode() == KeyCode.RIGHT) {
                showNext();
            }
        });

        setupEventHandlers();
    }

    private HBox createTitleBar() {
        HBox titleBar = new HBox();
        titleBar.setStyle(
            "-fx-background-color: " + COLOR_TITLE_BAR + ";" +
            "-fx-padding: 8 12 8 12;" +
            "-fx-alignment: center-left;"
        );
        titleBar.setPrefHeight(44);
        titleBar.setOnMousePressed(this::onTitleBarMousePressed);
        titleBar.setOnMouseDragged(this::onTitleBarMouseDragged);

        prevBtn = new Button("◀ 上一张");
        prevBtn.setStyle(getNavButtonStyle());
        prevBtn.setOnAction(e -> showPrevious());
        prevBtn.setDisable(currentIndex <= 0);

        indexLabel = new Label((currentIndex + 1) + " / " + imagePaths.size());
        indexLabel.setStyle(
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 0 20 0 20;"
        );

        nextBtn = new Button("下一张 ▶");
        nextBtn.setStyle(getNavButtonStyle());
        nextBtn.setOnAction(e -> showNext());
        nextBtn.setDisable(currentIndex >= imagePaths.size() - 1);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(getCloseButtonStyle());
        closeBtn.setOnAction(e -> dialog.close());
        closeBtn.setOnMouseEntered(e -> closeBtn.setStyle(getCloseButtonHoverStyle()));
        closeBtn.setOnMouseExited(e -> closeBtn.setStyle(getCloseButtonStyle()));

        HBox navGroup = new HBox(8, prevBtn, indexLabel, nextBtn);
        navGroup.setStyle("-fx-alignment: center;");

        titleBar.getChildren().addAll(navGroup, spacer, closeBtn);

        return titleBar;
    }

    private StackPane createContentArea() {
        StackPane contentArea = new StackPane();
        contentArea.setStyle("-fx-background-color: " + COLOR_BG_DARK + ";");

        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: " + COLOR_BG_DARK + "; -fx-border-color: transparent;");
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        StackPane imageContainer = new StackPane();
        imageContainer.setStyle("-fx-background-color: " + COLOR_BG_DARK + ";");

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        imageContainer.getChildren().add(imageView);
        scrollPane.setContent(imageContainer);

        imageInfoLabel = new Label();
        imageInfoLabel.setStyle(
            "-fx-text-fill: " + COLOR_TEXT_DIM + ";" +
            "-fx-font-size: 11px;" +
            "-fx-padding: 5;"
        );
        imageInfoLabel.setVisible(false);

        StackPane.setAlignment(scrollPane, javafx.geometry.Pos.CENTER);

        contentArea.getChildren().addAll(scrollPane, imageInfoLabel);
        StackPane.setAlignment(imageInfoLabel, javafx.geometry.Pos.BOTTOM_RIGHT);

        return contentArea;
    }

    private HBox createBottomBar() {
        HBox bottomBar = new HBox();
        bottomBar.setStyle(
            "-fx-background-color: " + COLOR_TITLE_BAR + ";" +
            "-fx-padding: 10 15 10 15;" +
            "-fx-alignment: center-right;"
        );
        bottomBar.setPrefHeight(52);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button resetBtn = createActionButton("重置 100%");
        resetBtn.setOnAction(e -> resetZoom());

        Button fitBtn = createActionButton("适应窗口");
        fitBtn.setOnAction(e -> fitToWindow());

        Button closeBtn = createActionButton("关闭");
        closeBtn.setOnAction(e -> dialog.close());

        HBox buttonGroup = new HBox(10, resetBtn, fitBtn);
        
        bottomBar.getChildren().addAll(buttonGroup, spacer, closeBtn);

        return bottomBar;
    }

    private Button createActionButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(getActionButtonStyle());
        btn.setOnMouseEntered(e -> btn.setStyle(getActionButtonHoverStyle()));
        btn.setOnMouseExited(e -> btn.setStyle(getActionButtonStyle()));
        return btn;
    }

    private void applyStyles(Scene scene) {
        String css = 
            ".scroll-bar:vertical {" +
            "    -fx-background-color: " + COLOR_BG_DARK + ";" +
            "}" +
            ".scroll-bar:vertical .thumb {" +
            "    -fx-background-color: #444444;" +
            "}" +
            ".scroll-bar:vertical .thumb:hover {" +
            "    -fx-background-color: #555555;" +
            "}" +
            ".scroll-bar:horizontal ," +
            ".scroll-bar:horizontal .thumb {" +
            "    -fx-background-color: transparent;" +
            "}";
        scene.getStylesheets().add("data:text/css," + css.replaceAll("\\s+", " "));
    }

    private String getNavButtonStyle() {
        return String.format(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: %s;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_TEXT
        );
    }

    private String getNavButtonHoverStyle() {
        return String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-size: 13px;" +
            "-fx-padding: 6 14 6 14;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_BUTTON_HOVER
        );
    }

    private String getCloseButtonStyle() {
        return String.format(
            "-fx-background-color: transparent;" +
            "-fx-text-fill: %s;" +
            "-fx-font-size: 16px;" +
            "-fx-padding: 6 10 6 10;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_TEXT_DIM
        );
    }

    private String getCloseButtonHoverStyle() {
        return String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-size: 16px;" +
            "-fx-padding: 6 10 6 10;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_CLOSE_HOVER
        );
    }

    private String getActionButtonStyle() {
        return String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: %s;" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 8 18 8 18;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_BUTTON_BG, COLOR_TEXT
        );
    }

    private String getActionButtonHoverStyle() {
        return String.format(
            "-fx-background-color: %s;" +
            "-fx-text-fill: #ffffff;" +
            "-fx-font-size: 12px;" +
            "-fx-padding: 8 18 8 18;" +
            "-fx-cursor: hand;" +
            "-fx-background-radius: 4;",
            COLOR_BUTTON_HOVER
        );
    }

    private void onTitleBarMousePressed(MouseEvent e) {
        dragX = e.getScreenX() - dialog.getX();
        dragY = e.getScreenY() - dialog.getY();
    }

    private void onTitleBarMouseDragged(MouseEvent e) {
        dialog.setX(e.getScreenX() - dragX);
        dialog.setY(e.getScreenY() - dragY);
        e.consume();
    }

    private void showPrevious() {
        if (currentIndex > 0) {
            currentIndex--;
            updateImage();
            updateNavigationButtons();
        }
    }

    private void showNext() {
        if (currentIndex < imagePaths.size() - 1) {
            currentIndex++;
            updateImage();
            updateNavigationButtons();
        }
    }

    private void updateImage() {
        currentImagePath = imagePaths.get(currentIndex);
        pathLabel.setText(currentImagePath.getFileName().toString());
        resetZoom();
        loadImage();
    }

    private void updateNavigationButtons() {
        prevBtn.setDisable(currentIndex <= 0);
        nextBtn.setDisable(currentIndex >= imagePaths.size() - 1);
        indexLabel.setText((currentIndex + 1) + " / " + imagePaths.size());
    }

    private void loadImage() {
        try {
            Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
            double maxW = screenBounds.getWidth() * 0.85;
            double maxH = screenBounds.getHeight() * 0.85;

            Image image = new Image(currentImagePath.toUri().toString(), false);
            double imgW = image.getWidth();
            double imgH = image.getHeight();

            if (imgW <= 0 || imgH <= 0) {
                imgW = 800;
                imgH = 600;
            }

            double fitScale = Math.min(maxW / imgW, maxH / imgH);
            fitScale = Math.min(fitScale, 1.0);

            targetFitWidth = imgW * fitScale;
            targetFitHeight = imgH * fitScale;
            zoomFactor = 1.0;

            imageView.setFitWidth(targetFitWidth);
            imageView.setFitHeight(targetFitHeight);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.setImage(image);

            imageInfoLabel.setText((int) imgW + " × " + (int) imgH);
            imageInfoLabel.setVisible(true);

            logger.info("Loaded image: {} ({}x{})",
                    currentImagePath.getFileName(),
                    (int) imgW,
                    (int) imgH);

            double dialogW = Math.min(targetFitWidth + 50, maxW + 50);
            double dialogH = Math.min(targetFitHeight + 120, maxH + 120);

            dialog.setWidth(Math.max(400, dialogW));
            dialog.setHeight(Math.max(300, dialogH));
            dialog.centerOnScreen();

        } catch (Exception e) {
            logger.error("Failed to load image: {}", currentImagePath, e);
        }
    }

    private void setupEventHandlers() {
        scrollPane.addEventFilter(ScrollEvent.ANY, e -> {
            if (e.getEventType() == ScrollEvent.SCROLL) {
                double delta = e.getDeltaY();
                double factor = delta > 0 ? 1.1 : 0.9;
                zoomFactor *= factor;
                zoomFactor = Math.max(0.1, Math.min(zoomFactor, 10.0));

                imageView.setScaleX(zoomFactor);
                imageView.setScaleY(zoomFactor);
                e.consume();
            }
        });
    }

    private void resetZoom() {
        zoomFactor = 1.0;
        imageView.setScaleX(zoomFactor);
        imageView.setScaleY(zoomFactor);
    }

    private void fitToWindow() {
        if (imageView.getImage() == null) return;

        Image img = imageView.getImage();
        double imgWidth = img.getWidth();
        double imgHeight = img.getHeight();
        if (imgWidth <= 0 || imgHeight <= 0) return;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double maxW = screenBounds.getWidth() * 0.9;
        double maxH = screenBounds.getHeight() * 0.9;

        double scaleW = maxW / imgWidth;
        double scaleH = maxH / imgHeight;
        zoomFactor = Math.min(scaleW, scaleH);

        imageView.setScaleX(zoomFactor);
        imageView.setScaleY(zoomFactor);

        dialog.setWidth(Math.min(imgWidth * zoomFactor + 50, maxW + 50));
        dialog.setHeight(Math.min(imgHeight * zoomFactor + 120, maxH + 120));
        dialog.centerOnScreen();
    }

    public void show() {
        dialog.show();
        Platform.runLater(this::loadImage);
    }
}
