package com.pixelj.ui;

import javafx.embed.swing.SwingFXUtils;
import javafx.event.EventHandler;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

/**
 * 图像单元格。
 * 使用 Canvas 绘制图像，支持悬停效果和点击事件。
 * 负责将 BufferedImage 转换为 JavaFX Image 并绘制。
 */
public class ImageCell extends Region {

    private static final Logger logger = LoggerFactory.getLogger(ImageCell.class);

    private final Canvas canvas;
    private BufferedImage bufferedImage;
    private String currentPath;
    private boolean isHovered = false;
    private boolean isSelected = false;
    private javafx.scene.image.Image fxImage;

    private EventHandler<MouseEvent> clickHandler;

    public ImageCell() {
        this.canvas = new Canvas();
        this.canvas.setStyle("-fx-background-color: #2a2a2a;");
        getChildren().add(canvas);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());

        widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        heightProperty().addListener((obs, oldVal, newVal) -> redraw());

        setOnMouseEntered(e -> {
            isHovered = true;
            redraw();
        });

        setOnMouseExited(e -> {
            isHovered = false;
            redraw();
        });

        setOnMouseClicked(e -> {
            if (clickHandler != null) {
                clickHandler.handle(e);
            }
        });
    }

    /**
     * 设置要显示的 BufferedImage。
     * 图像会被转换为 JavaFX Image 以在 Canvas 上绘制。
     *
     * @param image BufferedImage 图像
     */
    public void setBufferedImage(BufferedImage image) {
        this.bufferedImage = image;
        this.fxImage = null;
        if (image != null) {
            this.fxImage = SwingFXUtils.toFXImage(image, null);
        }
        redraw();
    }

    /**
     * 设置要显示的 JavaFX Image。
     *
     * @param image JavaFX Image
     */
    public void setFxImage(javafx.scene.image.Image image) {
        this.fxImage = image;
        this.bufferedImage = null;
        redraw();
    }

    /**
     * 获取当前缓存的 BufferedImage。
     *
     * @return BufferedImage 或 null
     */
    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    /**
     * 获取当前单元格关联的文件路径。
     *
     * @return 文件路径字符串
     */
    public String getCurrentPath() {
        return currentPath;
    }

    /**
     * 设置当前单元格关联的文件路径。
     *
     * @param path 文件路径
     */
    public void setCurrentPath(String path) {
        this.currentPath = path;
    }

    /**
     * 设置选中状态。
     *
     * @param selected 是否选中
     */
    public void setSelected(boolean selected) {
        this.isSelected = selected;
        redraw();
    }

    /**
     * 获取当前选中状态。
     *
     * @return 是否选中
     */
    public boolean isSelected() {
        return isSelected;
    }

    /**
     * 设置点击事件处理器。
     *
     * @param handler 鼠标事件处理器
     */
    public void setOnClick(EventHandler<MouseEvent> handler) {
        this.clickHandler = handler;
    }

    /**
     * 重绘单元格内容。
     * 绘制背景、图像或占位符。
     */
    private void redraw() {
        GraphicsContext ctx = canvas.getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) return;

        ctx.clearRect(0, 0, w, h);

        if (isHovered) {
            ctx.setFill(Color.rgb(60, 60, 60));
        } else {
            ctx.setFill(Color.rgb(42, 42, 42));
        }
        ctx.fillRect(0, 0, w, h);

        if (fxImage != null && !fxImage.isError()) {
            double imgW = fxImage.getWidth();
            double imgH = fxImage.getHeight();

            if (imgW > 0 && imgH > 0) {
                double scale = Math.min(w / imgW, h / imgH);
                double drawW = imgW * scale;
                double drawH = imgH * scale;
                double x = (w - drawW) / 2;
                double y = (h - drawH) / 2;

                ctx.drawImage(fxImage, x, y, drawW, drawH);
            }
        } else {
            ctx.setFill(Color.rgb(60, 60, 60));
            ctx.fillRect(2, 2, w - 4, h - 4);

            ctx.setStroke(Color.rgb(80, 80, 80));
            ctx.setLineWidth(1);
            ctx.strokeRect(2, 2, w - 4, h - 4);
        }

        if (isHovered) {
            ctx.setStroke(Color.rgb(100, 149, 237, 0.8));
            ctx.setLineWidth(2);
            ctx.strokeRect(1, 1, w - 2, h - 2);
        }

        if (isSelected) {
            ctx.setStroke(Color.rgb(255, 215, 0, 0.9));
            ctx.setLineWidth(3);
            ctx.strokeRect(1, 1, w - 2, h - 2);
        }
    }
}
