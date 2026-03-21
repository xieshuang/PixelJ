package com.pixelj.spi;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 图片解码器SPI接口
 * 
 * <p>用于插件化图片格式支持，所有解码器通过Java SPI机制加载。
 * 实现此接口可以添加新的图片格式支持，如RAW、WebP、HEIC等。
 * 
 * <p>使用示例：
 * <pre>{@code
 * // 通过SPI自动加载所有实现
 * ServiceLoader<ImageDecoder> loader = ServiceLoader.load(ImageDecoder.class);
 * for (ImageDecoder decoder : loader) {
 *     // 使用解码器
 * }
 * }</pre>
 * 
 * @author PixelJ Team
 * @version 1.0.0
 */
public interface ImageDecoder {

    /**
     * 解码配置选项
     * 
     * @param targetWidth  目标宽度，0表示原始宽度
     * @param targetHeight 目标高度，0表示原始高度
     * @param preserveAspectRatio 是否保持宽高比
     */
    record DecodeOptions(
            int targetWidth,
            int targetHeight,
            boolean preserveAspectRatio
    ) {
        /** 默认配置：不解码，直接返回原始尺寸 */
        public static final DecodeOptions DEFAULT = new DecodeOptions(0, 0, true);

        /**
         * 创建缩略图解码配置
         * 
         * @param width  缩略图宽度
         * @param height 缩略图高度
         * @return 保持宽高比的缩略图配置
         */
        public static DecodeOptions thumbnail(int width, int height) {
            return new DecodeOptions(width, height, true);
        }

        /**
         * 创建缩放解码配置
         * 
         * @param width  目标宽度
         * @param height 目标高度
         * @return 保持宽高比的缩放配置
         */
        public static DecodeOptions scaled(int width, int height) {
            return new DecodeOptions(width, height, true);
        }
    }

    /**
     * 图片元数据记录
     * 
     * @param camera       相机型号
     * @param lens         镜头型号
     * @param focalLength  焦距
     * @param aperture     光圈
     * @param shutterSpeed 快门速度
     * @param iso          ISO感光度
     * @param fileSize     文件大小（字节）
     * @param lastModified 最后修改时间（毫秒）
     * @param dateTaken    拍摄日期（毫秒时间戳）
     * @param latitude     GPS纬度
     * @param longitude    GPS经度
     */
    record ImageMetadata(
            String camera,
            String lens,
            String focalLength,
            String aperture,
            String shutterSpeed,
            String iso,
            long fileSize,
            long lastModified,
            long dateTaken,
            double latitude,
            double longitude
    ) {
    }

    /**
     * 获取支持的MIME类型列表
     * 
     * @return MIME类型数组，如 "image/jpeg", "image/png"
     */
    String[] supportedMimeTypes();

    /**
     * 获取支持的文件扩展名列表
     * 
     * @return 扩展名数组（不含点号），如 "jpg", "png", "webp"
     */
    String[] supportedExtensions();

    /**
     * 解码图片文件
     * 
     * <p>实现者应注意：
     * <ul>
     *   <li>线程安全：解码器可能被多线程并发调用</li>
     *   <li>资源释放：使用后应及时释放ImageReader等资源</li>
     *   <li>异常处理：解码失败应返回Optional.empty()，而非抛异常</li>
     * </ul>
     * 
     * @param path    图片文件路径
     * @param options 解码配置选项
     * @return 解码后的BufferedImage，若解码失败则返回空
     * @throws Exception 解码过程中的异常（如文件不存在、格式不支持等）
     */
    Optional<BufferedImage> decode(Path path, DecodeOptions options) throws Exception;

    /**
     * 读取图片元数据（EXIF信息等）
     * 
     * <p>默认实现返回空Optional，子类可重写以提供元数据读取功能。
     * 
     * @param path 图片文件路径
     * @return 图片元数据，若读取失败则返回空
     * @throws Exception 元数据读取过程中的异常
     */
    default Optional<ImageMetadata> readMetadata(Path path) throws Exception {
        return Optional.empty();
    }

    /**
     * 获取解码器优先级
     * 
     * <p>用于多个解码器支持同一格式时的优先级选择。
     * 数值越高优先级越高，默认100。
     * 
     * @return 优先级数值
     */
    default int getPriority() {
        return 100;
    }
}
