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
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
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
 * 以原始尺寸显示图片，支持缩放、拖动和上一张/下一张切换。
 */
public class ImageViewerDialog {

    private static final Logger logger = LoggerFactory.getLogger(ImageViewerDialog.class);

    private final Stage dialog;
    private final ImageView imageView;
    private final Label pathLabel;
    private final ScrollPane scrollPane;
    private final List<Path> imagePaths;
    private int currentIndex;
    private Button prevBtn;
    private Button nextBtn;
    private Label indexLabel;
    private double targetFitWidth = 0;
    private double targetFitHeight = 0;
    private double zoomFactor = 1.0;
    private Path currentImagePath;

    public ImageViewerDialog(List<Path> imagePaths, int initialIndex) {
        this.imagePaths = imagePaths;
        this.currentIndex = initialIndex;
        this.currentImagePath = imagePaths.get(initialIndex);
        this.dialog = new Stage(StageStyle.UTILITY);
        this.dialog.initModality(Modality.APPLICATION_MODAL);
        this.dialog.setTitle("原图 - " + currentImagePath.getFileName());
        this.dialog.setResizable(true);

        dialog.setWidth(800);
        dialog.setHeight(600);

        BorderPane root = new BorderPane();

        pathLabel = new Label(currentImagePath.toString());
        pathLabel.setStyle("-fx-background-color: #2a2a2a; -fx-text-fill: #ccc; -fx-padding: 5;");
        root.setTop(pathLabel);

        scrollPane = new ScrollPane();
        scrollPane.setStyle("-fx-background: #1a1a1a;");
        scrollPane.setPannable(true);

        StackPane imageContainer = new StackPane();
        imageContainer.setStyle("-fx-background-color: #1a1a1a;");

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(false);

        imageContainer.getChildren().add(imageView);
        scrollPane.setContent(imageContainer);
        root.setCenter(scrollPane);

        HBox buttonBar = new HBox(10);
        buttonBar.setStyle("-fx-background-color: #2a2a2a; -fx-padding: 10;");

        prevBtn = new Button("◀ 上一张");
        prevBtn.setOnAction(e -> showPrevious());
        prevBtn.setDisable(currentIndex <= 0);

        indexLabel = new Label((currentIndex + 1) + " / " + imagePaths.size());
        indexLabel.setStyle("-fx-text-fill: #ccc;");

        nextBtn = new Button("下一张 ▶");
        nextBtn.setOnAction(e -> showNext());
        nextBtn.setDisable(currentIndex >= imagePaths.size() - 1);

        Button resetBtn = new Button("重置 (100%)");
        resetBtn.setOnAction(e -> resetZoom());

        Button fitBtn = new Button("适应窗口");
        fitBtn.setOnAction(e -> fitToWindow());

        Button closeBtn = new Button("关闭");
        closeBtn.setOnAction(e -> dialog.close());

        buttonBar.getChildren().addAll(prevBtn, indexLabel, nextBtn, resetBtn, fitBtn, closeBtn);
        root.setBottom(buttonBar);

        Scene scene = new Scene(root);
        root.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");
        dialog.setScene(scene);

        dialog.setOnCloseRequest(e -> logger.debug("Image viewer closed"));

        dialog.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
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
        dialog.setTitle("原图 - " + currentImagePath.getFileName());
        pathLabel.setText(currentImagePath.toString());
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
            imageView.setSmooth(false);
            imageView.setImage(image);

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
