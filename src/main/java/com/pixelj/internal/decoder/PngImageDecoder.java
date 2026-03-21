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
 * PNG图片解码器
 * 
 * <p>使用JDK内置的ImageIO API解码PNG格式图片。
 * PNG格式支持无损压缩和透明度通道。
 * 
 * <p>优先级为90，低于JPEG（100），高于WebP（80）。
 * 
 * @author PixelJ Team
 * @see ImageDecoder
 */
public class PngImageDecoder implements ImageDecoder {

    @Override
    public String[] supportedMimeTypes() {
        return new String[]{"image/png"};
    }

    @Override
    public String[] supportedExtensions() {
        return new String[]{"png"};
    }

    /**
     * 解码PNG图片
     * 
     * <p>实现逻辑与JpegImageDecoder类似，
     * 区别在于使用PNG格式特定的MIME类型和较低的优先级。
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
     * <p>使用BICUBIC插值算法，与JPEG解码器保持一致。
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
                file.lastModified(),
                0, 0, 0
        ));
    }

    /** 优先级90：PNG格式优先级 */
    @Override
    public int getPriority() {
        return 90;
    }
}
