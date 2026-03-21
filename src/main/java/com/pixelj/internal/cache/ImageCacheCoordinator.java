package com.pixelj.internal.cache;

import com.pixelj.spi.ImageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 图片缓存协调器 - 三级缓存联动调度
 * 
 * <p>缓存加载顺序：
 * <ol>
 *   <li>L1内存缓存 - 快速访问，SoftReference防OOM</li>
 *   <li>L2磁盘缓存 - 缩略图持久化</li>
 *   <li>原始文件解码 - 最终回退</li>
 * </ol>
 * 
 * <p>设计原则：
 * <ul>
 *   <li>L1未命中时自动回退到L2</li>
 *   <li>L2未命中时自动解码原始文件</li>
 *   <li>解码后自动回填L1和L2缓存</li>
 *   <li>通过SPI自动加载所有解码器</li>
 * </ul>
 * 
 * @author PixelJ Team
 */
public class ImageCacheCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(ImageCacheCoordinator.class);

    private final L1MemoryCache l1Cache;
    private final L2DiskCache l2Cache;
    /** 正在加载的任务，防止重复加载 */
    private final ConcurrentHashMap<String, BufferedImage> loadingTasks;
    private final ExecutorService decoderExecutor;
    private final ImageDecoder[] decoders;

    /** 缩略图默认宽度 */
    public static final int THUMBNAIL_WIDTH = 400;
    /** 缩略图默认高度 */
    public static final int THUMBNAIL_HEIGHT = 300;

    /**
     * 创建缓存协调器
     * 
     * @param cacheRoot 缓存根目录（用于L2磁盘缓存）
     */
    public ImageCacheCoordinator(Path cacheRoot) {
        this.l1Cache = new L1MemoryCache();
        this.l2Cache = new L2DiskCache(cacheRoot);
        this.loadingTasks = new ConcurrentHashMap<>();
        this.decoderExecutor = createDecoderExecutor();
        this.decoders = loadDecoders();
        logger.info("ImageCacheCoordinator initialized with {} decoders", decoders.length);
    }

    /**
     * 创建图片解码线程池
     * 
     * <p>配置：
     * <ul>
     *   <li>线程数：CPU核心数</li>
     *   <li>线程名：image-decoder-pool-N</li>
     *   <li>优先级：最低，避免影响UI</li>
     *   <li>守护线程：允许JVM退出</li>
     * </ul>
     */
    private ExecutorService createDecoderExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Executors.newFixedThreadPool(cores, r -> {
            Thread t = new Thread(r, "image-decoder-pool-" + System.nanoTime());
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 加载所有SPI解码器
     * 
     * <p>通过Java SPI机制自动发现并加载所有ImageDecoder实现，
     * 按优先级排序。
     */
    private ImageDecoder[] loadDecoders() {
        ServiceLoader<ImageDecoder> loader = ServiceLoader.load(ImageDecoder.class);
        java.util.List<ImageDecoder> decoderList = new java.util.ArrayList<>();
        for (ImageDecoder decoder : loader) {
            decoderList.add(decoder);
        }
        // 按优先级降序排列
        decoderList.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return decoderList.toArray(new ImageDecoder[0]);
    }

    /**
     * 从缓存加载（不触发解码）
     * 
     * <p>依次检查L1和L2缓存，
     * L2命中时自动回填L1。
     * 
     * @param key 缓存键（文件绝对路径）
     * @return 缓存图片，若都不命中返回null
     */
    public BufferedImage loadFromCache(String key) {
        // L1缓存查找
        BufferedImage image = l1Cache.get(key);
        if (image != null) {
            logger.debug("L1 hit: {}", key);
            return image;
        }

        // L2缓存查找
        image = l2Cache.loadThumbnail(key);
        if (image != null) {
            logger.debug("L2 hit: {}", key);
            // 回填L1
            l1Cache.put(key, image);
            return image;
        }

        return null;
    }

    /**
     * 从原始文件加载（触发解码）
     * 
     * <p>完整加载流程：
     * <ol>
     *   <li>检查L1缓存</li>
     *   <li>检查L2缓存</li>
     *   <li>解码原始文件</li>
     *   <li>回填L2和L1缓存</li>
     * </ol>
     * 
     * @param path 图片路径
     * @return 解码后的缩略图
     */
    public BufferedImage loadFromOriginal(Path path) {
        String key = path.toAbsolutePath().toString();

        // 先检查缓存
        BufferedImage cached = loadFromCache(key);
        if (cached != null) {
            return cached;
        }

        // 解码原始文件
        BufferedImage image = decodeImage(path);
        if (image != null) {
            // 回填缓存
            l2Cache.saveThumbnail(key, image);
            l1Cache.put(key, image);
        }

        return image;
    }

    /**
     * 解码图片
     * 
     * <p>遍历所有解码器，尝试匹配格式。
     * 找到第一个支持的解码器后立即返回结果。
     * 
     * @param path 图片路径
     * @return 解码后的图片
     */
    private BufferedImage decodeImage(Path path) {
        for (ImageDecoder decoder : decoders) {
            try {
                String ext = getExtension(path);
                if (matchesDecoder(decoder, ext)) {
                    Optional<BufferedImage> result = decoder.decode(path,
                            ImageDecoder.DecodeOptions.thumbnail(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT));
                    if (result.isPresent()) {
                        logger.debug("Decoded with {}: {}", decoder.getClass().getSimpleName(), path);
                        return result.get();
                    }
                }
            } catch (Exception e) {
                logger.debug("Decoder {} failed for {}: {}",
                        decoder.getClass().getSimpleName(), path, e.getMessage());
            }
        }
        logger.warn("No decoder found for: {}", path);
        return null;
    }

    /**
     * 检查解码器是否支持指定扩展名
     */
    private boolean matchesDecoder(ImageDecoder decoder, String extension) {
        String ext = extension.toLowerCase();
        for (String supported : decoder.supportedExtensions()) {
            if (supported.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1) : "";
    }

    /**
     * 获取L1缓存实例
     */
    public L1MemoryCache getL1Cache() {
        return l1Cache;
    }

    /**
     * 获取L2缓存实例
     */
    public L2DiskCache getL2Cache() {
        return l2Cache;
    }

    /**
     * 获取解码器线程池
     */
    public ExecutorService getDecoderExecutor() {
        return decoderExecutor;
    }

    /**
     * 关闭协调器
     * 
     * <p>关闭解码线程池，
     * 等待最多5秒完成正在执行的任务。
     */
    public void shutdown() {
        decoderExecutor.shutdown();
        try {
            if (!decoderExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                decoderExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            decoderExecutor.shutdownNow();
        }
        logger.info("ImageCacheCoordinator shutdown");
    }
}
