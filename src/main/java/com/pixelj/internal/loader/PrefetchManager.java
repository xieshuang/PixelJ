package com.pixelj.internal.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 预取管理器，负责在用户滚动时提前加载即将可见的图像。
 * 通过监控滚动位置并预测用户视线范围内的图像来实现流畅的滚动体验。
 */
public class PrefetchManager {

    private static final Logger logger = LoggerFactory.getLogger(PrefetchManager.class);

    private static final int DEFAULT_PREFETCH_ROWS = 3;
    private static final int MAX_PREFETCH_QUEUE = 100;

    private final PriorityImageLoader imageLoader;
    private final ExecutorService prefetchExecutor;
    private final ConcurrentHashMap<Path, Long> prefetchHistory;
    private final AtomicLong prefetchCount;
    private final AtomicLong hitCount;

    private int prefetchRowCount;
    private boolean enabled;

    public PrefetchManager(PriorityImageLoader imageLoader) {
        this.imageLoader = imageLoader;
        this.prefetchExecutor = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "prefetch-pool");
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        });
        this.prefetchHistory = new ConcurrentHashMap<>();
        this.prefetchCount = new AtomicLong(0);
        this.hitCount = new AtomicLong(0);
        this.prefetchRowCount = DEFAULT_PREFETCH_ROWS;
        this.enabled = true;

        logger.info("PrefetchManager initialized with prefetchRows={}", prefetchRowCount);
    }

    /**
     * 根据当前可见索引更新预取任务。
     * 会在当前行前后一定范围内提交预取任务。
     *
     * @param allItems            所有图像路径列表
     * @param currentVisibleIndex 当前可见区域的起始索引
     * @param columnCount         列数
     */
    public void updatePrefetch(List<Path> allItems, int currentVisibleIndex, int columnCount) {
        if (!enabled || allItems.isEmpty()) {
            return;
        }

        prefetchExecutor.submit(() -> {
            int currentRow = currentVisibleIndex / columnCount;
            int startRow = Math.max(0, currentRow - prefetchRowCount);
            int endRow = currentRow + prefetchRowCount * 2;

            for (int row = startRow; row <= endRow; row++) {
                for (int col = 0; col < columnCount; col++) {
                    int index = row * columnCount + col;
                    if (index >= 0 && index < allItems.size()) {
                        Path path = allItems.get(index);

                        if (shouldPrefetch(path)) {
                            prefetch(path);
                        }
                    }
                }
            }
        });
    }

    private boolean shouldPrefetch(Path path) {
        long lastPrefetch = prefetchHistory.getOrDefault(path, 0L);
        long now = System.currentTimeMillis();

        if (now - lastPrefetch < 5000) {
            hitCount.incrementAndGet();
            return false;
        }

        return true;
    }

    /**
     * 执行单个图像的预取。
     *
     * @param path 图像路径
     */
    private void prefetch(Path path) {
        prefetchHistory.put(path, System.currentTimeMillis());
        prefetchCount.incrementAndGet();

        imageLoader.submit(path, PriorityImageLoader.Priority.MEDIUM);
    }

    public void setPrefetchRowCount(int rows) {
        this.prefetchRowCount = Math.max(1, Math.min(rows, 10));
        logger.info("Prefetch row count set to {}", this.prefetchRowCount);
    }

    public int getPrefetchRowCount() {
        return prefetchRowCount;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("PrefetchManager enabled={}", enabled);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PrefetchStats getStats() {
        return new PrefetchStats(
                prefetchCount.get(),
                hitCount.get(),
                prefetchHistory.size()
        );
    }

    public void clearHistory() {
        prefetchHistory.clear();
        logger.info("Prefetch history cleared");
    }

    public void shutdown() {
        prefetchExecutor.shutdown();
    }

    /**
     * 预取统计信息记录。
     *
     * @param totalPrefetch 预取总次数
     * @param totalHits     预取命中次数（5秒内重复请求）
     * @param historySize    预取历史记录数量
     */
    public record PrefetchStats(
            long totalPrefetch,
            long totalHits,
            int historySize
    ) {
    }
}
