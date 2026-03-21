package com.pixelj.internal.decoder;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.pixelj.spi.ImageDecoder;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Optional;
import java.util.TimeZone;

/**
 * JPEG图片解码器
 * 
 * <p>使用JDK内置的ImageIO API解码JPEG格式图片，
 * 支持EXIF元数据读取（通过扩展实现）。
 * 
 * <p>特性：
 * <ul>
 *   <li>支持 JPEG 和 JPG 扩展名</li>
 *   <li>支持指定尺寸的缩放解码</li>
 *   <li>使用BICUBIC插值算法保证缩放质量</li>
 * </ul>
 * 
 * @author PixelJ Team
 * @see ImageDecoder
 */
public class JpegImageDecoder implements ImageDecoder {

    /**
     * MIME类型列表
     */
    @Override
    public String[] supportedMimeTypes() {
        return new String[]{"image/jpeg", "image/jpg"};
    }

    /**
     * 支持的文件扩展名
     */
    @Override
    public String[] supportedExtensions() {
        return new String[]{"jpg", "jpeg"};
    }

    /**
     * 解码JPEG图片
     * 
     * <p>解码流程：
     * <ol>
     *   <li>检查文件是否可读（存在、可读、非空）</li>
     *   <li>创建ImageInputStream读取图片数据</li>
     *   <li>获取ImageReader并设置输入源</li>
     *   <li>读取原始图片，根据配置进行缩放</li>
     *   <li>释放ImageReader资源</li>
     * </ol>
     * 
     * @param path    图片路径
     * @param options 解码选项，支持指定目标尺寸
     * @return 解码后的图片，失败返回空
     */
    @Override
    public Optional<BufferedImage> decode(Path path, DecodeOptions options) throws Exception {
        // 检查文件是否可读
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

            // 获取JPEG解码器并解码
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                BufferedImage image = reader.read(0, reader.getDefaultReadParam());

                // 根据配置决定是否需要缩放
                if (options.targetWidth() > 0 && image.getWidth() > options.targetWidth()) {
                    return Optional.of(resizeImage(image, options.targetWidth(), options.targetHeight()));
                }
                return Optional.of(image);
            } finally {
                // 释放ImageReader资源，防止内存泄漏
                reader.dispose();
            }
        } catch (IOException e) {
            // IO异常返回空，不抛出
            return Optional.empty();
        }
    }

    /**
     * 检查文件是否可读
     * 
     * @param path 文件路径
     * @return 是否可读
     */
    private boolean isReadable(Path path) {
        java.io.File file = path.toFile();
        return file.exists() && file.canRead() && file.length() > 0;
    }

    /**
     * 图片缩放
     * 
     * <p>使用BICUBIC插值算法进行高质量缩放，
     * 目标高度为0时自动按宽高比计算。
     * 
     * @param source       源图片
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @return 缩放后的图片
     */
    private BufferedImage resizeImage(BufferedImage source, int targetWidth, int targetHeight) {
        // 自动计算高度（保持宽高比）
        if (targetHeight <= 0) {
            targetHeight = (int) (source.getHeight() * ((double) targetWidth / source.getWidth()));
        }

        // 确保正确的图片类型
        int type = source.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : source.getType();
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, type);

        // 使用高质量渲染参数
        java.awt.Graphics2D g = resized.createGraphics();
        try {
            // BICUBIC插值：适合照片缩放，质量好
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            // 渲染质量优先
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            // 关闭抗锯齿以提升性能
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
            
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            g.dispose();
        }

        return resized;
    }

    /**
     * 读取图片元数据
     * 
     * <p>使用 metadata-extractor 库读取 EXIF 信息，
     * 包括相机型号、拍摄日期和GPS位置。
     */
    @Override
    public Optional<ImageMetadata> readMetadata(Path path) throws Exception {
        File file = path.toFile();
        if (!file.exists()) {
            return Optional.empty();
        }

        String camera = null;
        String lens = null;
        String focalLength = null;
        String aperture = null;
        String shutterSpeed = null;
        String iso = null;
        long dateTaken = 0;
        double latitude = 0;
        double longitude = 0;

        try {
            Metadata metadata = JpegMetadataReader.readMetadata(file);

            ExifIFD0Directory exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0 != null) {
                camera = exifIFD0.getDescription(ExifIFD0Directory.TAG_MODEL);
            }

            ExifSubIFDDirectory exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifSubIFD != null) {
                lens = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
                focalLength = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                aperture = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_APERTURE);
                shutterSpeed = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                iso = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);

                Date date = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, TimeZone.getDefault());
                if (date != null) {
                    dateTaken = date.getTime();
                } else {
                    date = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME, TimeZone.getDefault());
                    if (date != null) {
                        dateTaken = date.getTime();
                    }
                }
            }

            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                com.drew.lang.GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null) {
                    latitude = geoLocation.getLatitude();
                    longitude = geoLocation.getLongitude();
                }
            }

        } catch (Exception e) {
            // 元数据读取失败，使用默认值
        }

        return Optional.of(new ImageMetadata(
                camera, lens, focalLength, aperture, shutterSpeed, iso,
                file.length(),
                file.lastModified(),
                dateTaken,
                latitude,
                longitude
        ));
    }

    /**
     * 解码器优先级：100
     */
    @Override
    public int getPriority() {
        return 100;
    }
}
