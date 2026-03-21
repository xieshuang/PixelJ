package com.pixelj.internal.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L2磁盘缓存 - 缩略图持久化存储
 * 
 * <p>缓存策略：
 * <ul>
 *   <li>缩略图存储在磁盘上，减少内存占用</li>
 *   <li>使用MD5哈希生成缩略图文件名，避免路径冲突</li>
 *   <li>超过空间限制时按LRU（最近最少使用）淘汰</li>
 * </ul>
 * 
 * <p>存储结构：
 * <pre>
 * cacheRoot/
 * └── .pixelj_thumbnails/
 *     ├── thumb_a1b2c3d4.jpg
 *     ├── thumb_e5f6g7h8.jpg
 *     └── ...
 * </pre>
 * 
 * @author PixelJ Team
 */
public class L2DiskCache {

    private static final Logger logger = LoggerFactory.getLogger(L2DiskCache.class);

    /** 默认最大磁盘空间：1GB */
    private static final long DEFAULT_MAX_DISK_SIZE = 1024 * 1024 * 1024L;
    /** 默认缩略图宽度 */
    private static final int DEFAULT_THUMBNAIL_WIDTH = 400;
    /** 默认缩略图高度 */
    private static final int DEFAULT_THUMBNAIL_HEIGHT = 300;
    /** JPEG压缩质量：80% */
    private static final float JPEG_QUALITY = 0.8f;
    /** 缩略图目录名 */
    private static final String THUMBNAIL_DIR = ".pixelj_thumbnails";
    /** 缩略图文件名前缀 */
    private static final String THUMBNAIL_PREFIX = "thumb_";
    /** 缩略图文件扩展名 */
    private static final String THUMBNAIL_EXTENSION = ".jpg";

    private final Path cacheRoot;
    private final long maxDiskSize;
    private final int thumbnailWidth;
    private final int thumbnailHeight;
    private final AtomicLong currentDiskSize;
    /** 内存索引：originalPath -> thumbnailPath */
    private final ConcurrentHashMap<String, Path> thumbnailIndex;

    /**
     * 创建默认配置的L2磁盘缓存
     * 
     * @param cacheRoot 缓存根目录
     */
    public L2DiskCache(Path cacheRoot) {
        this(cacheRoot, DEFAULT_MAX_DISK_SIZE);
    }

    /**
     * 创建自定义配置的L2磁盘缓存
     * 
     * @param cacheRoot   缓存根目录
     * @param maxDiskSize 最大磁盘空间（字节）
     */
    public L2DiskCache(Path cacheRoot, long maxDiskSize) {
        this.cacheRoot = cacheRoot;
        this.maxDiskSize = maxDiskSize;
        this.thumbnailWidth = DEFAULT_THUMBNAIL_WIDTH;
        this.thumbnailHeight = DEFAULT_THUMBNAIL_HEIGHT;
        this.currentDiskSize = new AtomicLong(0);
        this.thumbnailIndex = new ConcurrentHashMap<>();
        initialize();
    }

    /**
     * 初始化缓存目录
     */
    private void initialize() {
        try {
            Path thumbnailDir = cacheRoot.resolve(THUMBNAIL_DIR);
            if (!Files.exists(thumbnailDir)) {
                Files.createDirectories(thumbnailDir);
            }
            calculateCurrentSize(thumbnailDir);
            startCleanupThread();
            logger.info("L2DiskCache initialized: root={}, maxSize={}MB",
                    thumbnailDir, maxDiskSize / (1024 * 1024));
        } catch (IOException e) {
            logger.error("Failed to initialize L2 disk cache", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成缩略图文件名
     * 
     * <p>使用MD5哈希确保唯一性，避免长路径问题。
     * 例如：原路径 "C:\Photos\Vacation\IMG_0001.JPG"
     * 生成文件名 "thumb_a1b2c3d4e5f6g7h8i9j0.jpg"
     * 
     * @param originalPath 原始文件路径
     * @return 缩略图文件名
     */
    private String generateThumbnailKey(String originalPath) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(originalPath.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return THUMBNAIL_PREFIX + hexString + THUMBNAIL_EXTENSION;
        } catch (Exception e) {
            return THUMBNAIL_PREFIX + originalPath.hashCode() + THUMBNAIL_EXTENSION;
        }
    }

    /**
     * 获取缩略图路径
     * 
     * @param originalPath 原始文件路径
     * @return 缩略图完整路径
     */
    public Path getThumbnailPath(String originalPath) {
        return cacheRoot.resolve(THUMBNAIL_DIR).resolve(generateThumbnailKey(originalPath));
    }

    /**
     * 保存缩略图到磁盘
     * 
     * <p>保存流程：
     * <ol>
     *   <li>生成缩略图路径</li>
     *   <li>使用JPEG编码保存（80%质量）</li>
     *   <li>更新内存索引和磁盘占用统计</li>
     *   <li>超过空间限制时触发清理</li>
     * </ol>
     * 
     * @param originalPath 原始文件路径
     * @param image       缩略图图片
     * @return 是否保存成功
     */
    public boolean saveThumbnail(String originalPath, BufferedImage image) {
        Path thumbnailPath = getThumbnailPath(originalPath);

        try {
            Files.createDirectories(thumbnailPath.getParent());

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) {
                logger.warn("No JPEG writer available");
                return false;
            }

            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);

            ImageIO.write(image, "jpg", thumbnailPath.toFile());

            long fileSize = Files.size(thumbnailPath);
            thumbnailIndex.put(originalPath, thumbnailPath);
            currentDiskSize.addAndGet(fileSize);

            // 超过空间限制时触发异步清理
            if (currentDiskSize.get() > maxDiskSize) {
                scheduleCleanup();
            }

            return true;
        } catch (IOException e) {
            logger.error("Failed to save thumbnail: {}", originalPath, e);
            return false;
        }
    }

    /**
     * 从磁盘加载缩略图
     * 
     * @param originalPath 原始文件路径
     * @return 缩略图图片，若不存在则返回null
     */
    public BufferedImage loadThumbnail(String originalPath) {
        Path thumbnailPath = thumbnailIndex.get(originalPath);

        if (thumbnailPath == null) {
            thumbnailPath = getThumbnailPath(originalPath);
        }

        if (!Files.exists(thumbnailPath)) {
            return null;
        }

        try {
            return ImageIO.read(Files.newInputStream(thumbnailPath));
        } catch (IOException e) {
            logger.error("Failed to load thumbnail: {}", originalPath, e);
            return null;
        }
    }

    /**
     * 检查缩略图是否存在
     * 
     * @param originalPath 原始文件路径
     * @return 是否存在
     */
    public boolean hasThumbnail(String originalPath) {
        Path thumbnailPath = thumbnailIndex.get(originalPath);
        if (thumbnailPath != null && Files.exists(thumbnailPath)) {
            return true;
        }
        thumbnailPath = getThumbnailPath(originalPath);
        return Files.exists(thumbnailPath);
    }

    /**
     * 删除指定缩略图
     * 
     * @param originalPath 原始文件路径
     * @return 是否删除成功
     */
    public boolean deleteThumbnail(String originalPath) {
        Path thumbnailPath = thumbnailIndex.remove(originalPath);
        if (thumbnailPath == null) {
            thumbnailPath = getThumbnailPath(originalPath);
        }

        if (Files.exists(thumbnailPath)) {
            try {
                long size = Files.size(thumbnailPath);
                Files.delete(thumbnailPath);
                currentDiskSize.addAndGet(-size);
                return true;
            } catch (IOException e) {
                logger.error("Failed to delete thumbnail: {}", originalPath, e);
            }
        }
        return false;
    }

    /**
     * 清空所有缩略图缓存
     */
    public void clear() {
        try {
            Path thumbnailDir = cacheRoot.resolve(THUMBNAIL_DIR);
            if (Files.exists(thumbnailDir)) {
                // 按深度降序排列，确保先删除子文件再删除目录
                Files.walk(thumbnailDir)
                        .sorted((a, b) -> -a.compareTo(b))
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                            }
                        });
            }
            thumbnailIndex.clear();
            currentDiskSize.set(0);
            logger.info("L2DiskCache cleared");
        } catch (IOException e) {
            logger.error("Failed to clear L2 disk cache", e);
        }
    }

    /**
     * 获取当前磁盘占用
     */
    public long getCurrentDiskSize() {
        return currentDiskSize.get();
    }

    /**
     * 获取最大磁盘空间限制
     */
    public long getMaxDiskSize() {
        return maxDiskSize;
    }

    /**
     * 计算当前缩略图目录大小
     */
    private void calculateCurrentSize(Path thumbnailDir) {
        if (!Files.exists(thumbnailDir)) return;

        try {
            long size = Files.walk(thumbnailDir)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
            currentDiskSize.set(size);
        } catch (IOException e) {
            currentDiskSize.set(0);
        }
    }

    /**
     * 调度异步清理任务
     */
    private void scheduleCleanup() {
        Thread cleanupThread = new Thread(this::performCleanup, "l2-cache-cleanup");
        cleanupThread.setPriority(Thread.MIN_PRIORITY);
        cleanupThread.start();
    }

    /**
     * 执行LRU清理
     * 
     * <p>按最后修改时间排序，删除最老的文件，
     * 直到空间降到70%以下。
     */
    private void performCleanup() {
        long targetSize = (long) (maxDiskSize * 0.7);

        try {
            Path thumbnailDir = cacheRoot.resolve(THUMBNAIL_DIR);
            if (!Files.exists(thumbnailDir)) return;

            Files.list(thumbnailDir)
                    .filter(p -> p.getFileName().toString().startsWith(THUMBNAIL_PREFIX))
                    .sorted((a, b) -> {
                        try {
                            // 按修改时间升序，最老的在前
                            return Long.compare(Files.getLastModifiedTime(a).toMillis(),
                                    Files.getLastModifiedTime(b).toMillis());
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .forEach(p -> {
                        if (currentDiskSize.get() <= targetSize) return;
                        try {
                            long size = Files.size(p);
                            Files.delete(p);
                            currentDiskSize.addAndGet(-size);
                            thumbnailIndex.entrySet().removeIf(e -> e.getValue().equals(p));
                        } catch (IOException e) {
                            logger.error("Failed to delete during cleanup: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            logger.error("Cleanup failed", e);
        }
    }

    /**
     * 启动后台清理线程
     * 
     * <p>每60秒检查一次磁盘占用，
     * 超过限制时执行清理。
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000);
                    if (currentDiskSize.get() > maxDiskSize) {
                        performCleanup();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "l2-disk-cleanup");
        cleanupThread.setPriority(Thread.MIN_PRIORITY);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
}
