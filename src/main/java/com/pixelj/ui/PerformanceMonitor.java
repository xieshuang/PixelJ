package com.pixelj.ui;

import com.pixelj.internal.cache.ImageCacheCoordinator;
import com.pixelj.internal.cache.L1MemoryCache;
import com.pixelj.internal.cache.L2DiskCache;
import com.pixelj.internal.loader.PrefetchManager;
import com.pixelj.util.MemoryMonitor;
import com.pixelj.util.ScrollPerformanceTracker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 性能监控器。
 * 实时显示 FPS、内存使用、缓存状态和预取统计等信息。
 * 监控数据以固定间隔更新。
 */
public class PerformanceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    private final ImageCacheCoordinator cacheCoordinator;
    private final PrefetchManager prefetchManager;
    private final MemoryMonitor memoryMonitor;
    private final ScrollPerformanceTracker scrollTracker;

    private final ScheduledExecutorService scheduler;
    private final Label fpsLabel;
    private final Label memoryLabel;
    private final Label cacheLabel;
    private final Label prefetchLabel;

    private boolean visible;

    public PerformanceMonitor(
            ImageCacheCoordinator cacheCoordinator,
            PrefetchManager prefetchManager) {
        this.cacheCoordinator = cacheCoordinator;
        this.prefetchManager = prefetchManager;
        this.memoryMonitor = new MemoryMonitor();
        this.scrollTracker = new ScrollPerformanceTracker();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "perf-monitor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

        this.fpsLabel = new Label("FPS: --");
        this.memoryLabel = new Label("Memory: --");
        this.cacheLabel = new Label("Cache: --");
        this.prefetchLabel = new Label("Prefetch: --");

        styleLabels();
        startUpdating();
        logger.info("PerformanceMonitor initialized");
    }

    /**
     * 设置标签样式。
     * 使用等宽字体和绿色文字显示。
     */
    private void styleLabels() {
        String style = "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                       "-fx-font-size: 11px; " +
                       "-fx-text-fill: #00ff00;";
        fpsLabel.setStyle(style);
        memoryLabel.setStyle(style);
        cacheLabel.setStyle(style);
        prefetchLabel.setStyle(style);
    }

    /**
     * 启动定期更新任务。
     * 每秒更新一次性能数据。
     */
    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                update();
            } catch (Exception e) {
                logger.error("Error updating performance monitor", e);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * 更新性能数据。
     */
    private void update() {
        if (!visible) return;

        long fps = scrollTracker.getFps();
        String fpsText = fps >= 55 ? "FPS: " + fps :
                         fps >= 30 ? "FPS: " + fps + " (low)" : "FPS: " + fps + " (dropping)";

        MemoryMonitor.MemoryStats memStats = memoryMonitor.getStats();
        String memText = String.format("Memory: %s / %s (%.0f%%)",
                memStats.heapUsedMB(),
                memStats.heapMaxMB(),
                memStats.heapUsageRatio() * 100);

        L1MemoryCache l1 = cacheCoordinator.getL1Cache();
        L2DiskCache l2 = cacheCoordinator.getL2Cache();
        String cacheText = String.format("L1: %d items, L2: %.1fMB",
                l1.size(),
                l2.getCurrentDiskSize() / (1024.0 * 1024));

        PrefetchManager.PrefetchStats prefetchStats = prefetchManager.getStats();
        String prefetchText = String.format("Prefetch: %d loaded, %d hits",
                prefetchStats.totalPrefetch(),
                prefetchStats.totalHits());

        updateLabels(fpsText, memText, cacheText, prefetchText);
    }

    /**
     * 在 UI 线程上更新标签文本。
     *
     * @param fps      FPS 文本
     * @param mem      内存文本
     * @param cache    缓存文本
     * @param prefetch 预取文本
     */
    private void updateLabels(String fps, String mem, String cache, String prefetch) {
        javafx.application.Platform.runLater(() -> {
            fpsLabel.setText(fps);
            memoryLabel.setText(mem);
            cacheLabel.setText(cache);
            prefetchLabel.setText(prefetch);
        });
    }

    /**
     * 创建性能监控条。
     *
     * @return HBox 包含所有监控标签
     */
    public HBox createMonitorBar() {
        HBox bar = new HBox(15);
        bar.setPadding(new Insets(5, 10, 5, 10));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setStyle("-fx-background-color: rgba(0,0,0,0.7);");

        bar.getChildren().addAll(fpsLabel, memoryLabel, cacheLabel, prefetchLabel);

        return bar;
    }

    /**
     * 设置监控器可见性。
     *
     * @param visible 是否可见
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * 获取监控器可见性状态。
     *
     * @return 是否可见
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * 获取滚动性能跟踪器。
     *
     * @return ScrollPerformanceTracker
     */
    public ScrollPerformanceTracker getScrollTracker() {
        return scrollTracker;
    }

    /**
     * 获取内存监控器。
     *
     * @return MemoryMonitor
     */
    public MemoryMonitor getMemoryMonitor() {
        return memoryMonitor;
    }

    /**
     * 开始滚动跟踪。
     */
    public void startScrollTracking() {
        scrollTracker.startTracking();
    }

    /**
     * 停止滚动跟踪。
     */
    public void stopScrollTracking() {
        scrollTracker.stopTracking();
    }

    /**
     * 关闭性能监控器。
     */
    public void shutdown() {
        scheduler.shutdown();
        memoryMonitor.shutdown();
        prefetchManager.shutdown();
        logger.info("PerformanceMonitor shutdown");
    }
}
