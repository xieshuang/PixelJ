package com.pixelj.internal.decoder;

import com.pixelj.spi.ImageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 图像解码器服务，通过SPI机制加载和管理所有图像解码器。
 * 提供解码器查询和支持的文件格式检查功能。
 * 采用单例模式确保解码器只加载一次。
 */
public class ImageDecoderService {

    private static final Logger logger = LoggerFactory.getLogger(ImageDecoderService.class);

    private static volatile ImageDecoderService instance;
    private final ImageDecoder[] decoders;
    private final Set<String> supportedExtensions;

    private ImageDecoderService() {
        this.decoders = loadDecoders();
        this.supportedExtensions = Arrays.stream(decoders)
                .flatMap(d -> Arrays.stream(d.supportedExtensions()))
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        logger.info("ImageDecoderService loaded {} decoders, supporting {} extensions",
                decoders.length, supportedExtensions.size());
    }

    /**
     * 获取 ImageDecoderService 单例实例。
     * 采用双重检查锁定确保线程安全。
     *
     * @return ImageDecoderService 实例
     */
    public static ImageDecoderService getInstance() {
        if (instance == null) {
            synchronized (ImageDecoderService.class) {
                if (instance == null) {
                    instance = new ImageDecoderService();
                }
            }
        }
        return instance;
    }

    private ImageDecoder[] loadDecoders() {
        ServiceLoader<ImageDecoder> loader = ServiceLoader.load(ImageDecoder.class);
        List<ImageDecoder> decoderList = new ArrayList<>();
        for (ImageDecoder decoder : loader) {
            decoderList.add(decoder);
            logger.info("Loaded decoder: {} for {}",
                    decoder.getClass().getSimpleName(),
                    Arrays.toString(decoder.supportedExtensions()));
        }
        decoderList.sort(Comparator.comparingInt(ImageDecoder::getPriority).reversed());
        return decoderList.toArray(new ImageDecoder[0]);
    }

    /**
     * 获取所有已加载的解码器数组。
     * 解码器按优先级降序排列。
     *
     * @return 解码器数组
     */
    public ImageDecoder[] getDecoders() {
        return decoders;
    }

    /**
     * 检查给定文件扩展名是否支持。
     *
     * @param extension 文件扩展名（不含点号）
     * @return 是否支持
     */
    public boolean isSupported(String extension) {
        return supportedExtensions.contains(extension.toLowerCase());
    }

    /**
     * 获取所有支持的文件扩展名集合。
     *
     * @return 支持的扩展名集合
     */
    public Set<String> getSupportedExtensions() {
        return new HashSet<>(supportedExtensions);
    }
}
