package com.pixelj.internal.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * L1内存缓存 - 基于SoftReference的LRU缓存
 * 
 * <p>缓存策略：
 * <ul>
 *   <li>使用SoftReference防止OOM - JVM内存不足时自动回收</li>
 *   <li>使用LinkedHashMap实现LRU - 最近最少使用的条目被优先淘汰</li>
 *   <li>双重淘汰机制：内存阈值 + 最大条目数</li>
 * </ul>
 * 
 * <p>内存计算：
 * 每张图片内存占用 = 宽度 × 高度 × 4字节（ARGB）
 * 例如：4000×3000图片 ≈ 48MB
 * 
 * @author PixelJ Team
 */
public class L1MemoryCache {

    private static final Logger logger = LoggerFactory.getLogger(L1MemoryCache.class);

    /** 默认最大内存：512MB */
    private static final long DEFAULT_MAX_MEMORY = 512 * 1024 * 1024L;
    /** 默认最大条目数：200 */
    private static final int DEFAULT_MAX_COUNT = 200;

    private final long maxMemoryBytes;
    private final int maxCount;
    private final Map<String, CacheEntry> cache;
    private final ReferenceQueue<BufferedImage> referenceQueue;
    private final AtomicLong currentMemoryBytes;

    /**
     * 缓存条目
     * 
     * <p>包含图片的软引用（允许GC回收）、
     * 内存大小计算和访问时间记录。
     */
    private static class CacheEntry {
        final SoftReference<BufferedImage> imageRef;
        final int memorySizeBytes;
        long lastAccessTime;

        CacheEntry(BufferedImage image, ReferenceQueue<BufferedImage> queue) {
            this.imageRef = new SoftReference<>(image, queue);
            // 计算图片内存占用：宽×高×4字节（ARGB）
            this.memorySizeBytes = calculateMemorySize(image);
            this.lastAccessTime = System.currentTimeMillis();
        }

        BufferedImage getImage() {
            return imageRef.get();
        }

        void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }

    /**
     * 创建默认配置的L1缓存
     * 
     * <p>默认配置：最大512MB内存，最多200条缓存
     */
    public L1MemoryCache() {
        this(DEFAULT_MAX_MEMORY, DEFAULT_MAX_COUNT);
    }

    /**
     * 创建自定义配置的L1缓存
     * 
     * @param maxMemoryBytes 最大内存占用（字节）
     * @param maxCount       最大缓存条目数
     */
    public L1MemoryCache(long maxMemoryBytes, int maxCount) {
        this.maxMemoryBytes = maxMemoryBytes;
        this.maxCount = maxCount;
        
        // 创建LRU缓存：accessOrder=true表示按访问顺序排序
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                // 只有超过最大条目数时才淘汰，不在这里淘汰超内存的
                if (size() <= maxCount) {
                    return false;
                }
                currentMemoryBytes.addAndGet(-eldest.getValue().memorySizeBytes);
                return true;
            }
        };
        this.referenceQueue = new ReferenceQueue<>();
        this.currentMemoryBytes = new AtomicLong(0);
        
        // 启动后台清理线程
        startCleanupThread();
        logger.info("L1MemoryCache initialized: maxMemory={}MB, maxCount={}",
                maxMemoryBytes / (1024 * 1024), maxCount);
    }

    /**
     * 计算图片内存占用
     * 
     * @param image BufferedImage对象
     * @return 内存占用字节数
     */
    private static int calculateMemorySize(BufferedImage image) {
        return image.getWidth() * image.getHeight() * 4;
    }

    /**
     * 存入缓存
     * 
     * <p>如果key已存在，会先移除旧条目。
     * 存入后检查是否需要淘汰。
     * 
     * @param key    缓存键（通常为文件路径）
     * @param image  图片对象
     */
    public void put(String key, BufferedImage image) {
        if (image == null) return;

        synchronized (cache) {
            // 清理已被GC回收的软引用
            cleanUpQueue();

            // 移除旧条目，更新内存统计
            CacheEntry existing = cache.get(key);
            if (existing != null) {
                currentMemoryBytes.addAndGet(-existing.memorySizeBytes);
            }

            CacheEntry entry = new CacheEntry(image, referenceQueue);
            cache.put(key, entry);
            currentMemoryBytes.addAndGet(entry.memorySizeBytes);

            // 超过内存限制时进行淘汰
            evictIfNecessary();
        }
    }

    /**
     * 获取缓存
     * 
     * <p>如果图片已被GC回收，返回null。
     * 访问时会更新LRU顺序。
     * 
     * @param key 缓存键
     * @return 图片对象，若不存在或已被回收则返回null
     */
    public BufferedImage get(String key) {
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) return null;

            BufferedImage image = entry.getImage();
            if (image == null) {
                // 已被GC回收，清理条目
                currentMemoryBytes.addAndGet(-entry.memorySizeBytes);
                cache.remove(key);
                return null;
            }

            // 更新访问时间（维护LRU顺序）
            entry.updateAccessTime();
            return image;
        }
    }

    /**
     * 检查key是否存在且有效
     * 
     * @param key 缓存键
     * @return 是否存在
     */
    public boolean containsKey(String key) {
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            return entry != null && entry.getImage() != null;
        }
    }

    /**
     * 移除指定缓存
     * 
     * @param key 缓存键
     */
    public void remove(String key) {
        synchronized (cache) {
            CacheEntry entry = cache.remove(key);
            if (entry != null) {
                currentMemoryBytes.addAndGet(-entry.memorySizeBytes);
            }
        }
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        synchronized (cache) {
            cache.clear();
            currentMemoryBytes.set(0);
        }
        logger.info("L1MemoryCache cleared");
    }

    /**
     * 获取当前缓存条目数
     * 
     * @return 缓存数量
     */
    public int size() {
        synchronized (cache) {
            cleanUpQueue();
            return cache.size();
        }
    }

    /**
     * 获取已使用内存字节数
     * 
     * @return 已用内存
     */
    public long getUsedMemoryBytes() {
        return currentMemoryBytes.get();
    }

    /**
     * 获取最大内存限制
     * 
     * @return 最大内存
     */
    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    /**
     * 内存超限时进行淘汰
     * 
     * <p>遍历LinkedHashMap的迭代顺序就是LRU顺序，
     * 从头开始淘汰直到内存降到限制以下。
     */
    private void evictIfNecessary() {
        while (currentMemoryBytes.get() > maxMemoryBytes && !cache.isEmpty()) {
            Map.Entry<String, CacheEntry> eldest = cache.entrySet().iterator().next();
            currentMemoryBytes.addAndGet(-eldest.getValue().memorySizeBytes);
            cache.remove(eldest.getKey());
            logger.debug("Evicted from L1 cache: {}", eldest.getKey());
        }
    }

    /**
     * 清理ReferenceQueue中的已回收引用
     * 
     * <p>当图片被GC回收时，其SoftReference会被加入ReferenceQueue。
     * 此方法遍历队列，移除对应的缓存条目。
     */
    private void cleanUpQueue() {
        Reference<?> ref;
        while ((ref = referenceQueue.poll()) != null) {
            for (Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, CacheEntry> entry = it.next();
                if (entry.getValue().imageRef == ref) {
                    currentMemoryBytes.addAndGet(-entry.getValue().memorySizeBytes);
                    it.remove();
                }
            }
        }
    }

    /**
     * 启动后台清理线程
     * 
     * <p>每60秒检查一次ReferenceQueue，
     * 清理已被GC回收的软引用。
     * 线程优先级最低，不影响主程序运行。
     */
    private void startCleanupThread() {
        Thread cleanupThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60000);
                    synchronized (cache) {
                        cleanUpQueue();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "l1-cache-cleanup");
        cleanupThread.setPriority(Thread.MIN_PRIORITY);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }
}
