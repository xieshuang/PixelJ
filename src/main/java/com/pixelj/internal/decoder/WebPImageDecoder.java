package com.pixelj.internal.decoder;

import com.pixelj.spi.ImageDecoder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * WebP图片解码器
 * 
 * <p>使用 TwelveMonkeys ImageIO 插件解码WebP格式图片。
 * WebP是一种现代图片格式，提供出色的压缩率。
 * 
 * <p>需要十二猴子库（twelvemonkeys）的imageio-webp组件支持。
 * 优先级为80，低于内置格式。
 * 
 * @author PixelJ Team
 * @see ImageDecoder
 */
public class WebPImageDecoder implements ImageDecoder {

    @Override
    public String[] supportedMimeTypes() {
        return new String[]{"image/webp"};
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"webp"};
    }

    /**
     * 解码WebP图片
     * 
     * <p>通过TwelveMonkeys的ImageReader实现WebP解码，
     * 解码后根据配置进行缩放处理。
     * 
     * @param path    图片路径
     * @param options 解码选项
     * @return 解码后的图片
     */
    @Override
    public Optional<BufferedImage> decode(Path path, DecodeOptions options) throws Exception {
        if (!isReadable(path)) {
            return Optional.empty();
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            if (input == null) {
                return Optional.empty();
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return Optional.empty();
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                BufferedImage image = reader.read(0, reader.getDefaultReadParam());

                if (options.targetWidth() > 0 && image.getWidth() > options.targetWidth()) {
                    return Optional.of(resizeImage(image, options.targetWidth(), options.targetHeight()));
                }
                return Optional.of(image);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private boolean isReadable(Path path) {
        java.io.File file = path.toFile();
        return file.exists() && file.canRead() && file.length() > 0;
    }

    /**
     * 图片缩放
     * 
     * <p>使用BICUBIC插值算法，与其他解码器保持一致。
     */
    private BufferedImage resizeImage(BufferedImage source, int targetWidth, int targetHeight) {
        if (targetHeight <= 0) {
            targetHeight = (int) (source.getHeight() * ((double) targetWidth / source.getWidth()));
        }

        int type = source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, type);

        java.awt.Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        return resized;
    }

    @Override
    public Optional<ImageMetadata> readMetadata(Path path) throws Exception {
        java.io.File file = path.toFile();
        if (!file.exists()) {
            return Optional.empty();
        }
        return Optional.of(new ImageMetadata(
                null, null, null, null, null, null,
                file.length(),
                file.lastModified()
        ));
    }

    /** 优先级80：WebP为扩展格式，使用较低优先级 */
    @Override
    public int getPriority() {
        return 80;
    }
}
