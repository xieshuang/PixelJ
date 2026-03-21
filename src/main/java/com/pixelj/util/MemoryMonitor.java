package com.pixelj.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 内存监控器。
 * 定期检查 JVM 堆内存使用情况，在内存使用超过阈值时触发警告或关键警报。
 * 支持注册监听器以在内存紧张时执行相应操作。
 */
public class MemoryMonitor {

    private static final Logger logger = LoggerFactory.getLogger(MemoryMonitor.class);

    private static final double WARNING_THRESHOLD = 0.85;
    private static final double CRITICAL_THRESHOLD = 0.95;

    private final MemoryMXBean memoryBean;
    private final ScheduledExecutorService scheduler;
    private final Runtime runtime;

    private long maxMemory;
    private MemoryWarningListener warningListener;
    private MemoryCriticalListener criticalListener;

    public MemoryMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.runtime = Runtime.getRuntime();
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "memory-monitor");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        this.maxMemory = memoryBean.getHeapMemoryUsage().getMax();

        startMonitoring();
        logger.info("MemoryMonitor started. Max heap: {}MB", maxMemory / (1024 * 1024));
    }

    /**
     * 启动定期内存检查任务。
     * 每5秒执行一次内存检查。
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkMemory();
            } catch (Exception e) {
                logger.error("Error during memory check", e);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * 执行内存检查。
     * 根据内存使用率触发相应的监听器回调。
     */
    private void checkMemory() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();

        if (max <= 0) {
            max = runtime.maxMemory();
        }

        double usageRatio = (double) used / max;

        if (usageRatio >= CRITICAL_THRESHOLD) {
            triggerGC();
            if (criticalListener != null) {
                criticalListener.onCritical(used, max, usageRatio);
            }
            logger.warn("Memory critical: {}MB / {}MB ({:.1f}%)",
                    used / (1024 * 1024), max / (1024 * 1024), usageRatio * 100);
        } else if (usageRatio >= WARNING_THRESHOLD) {
            if (warningListener != null) {
                warningListener.onWarning(used, max, usageRatio);
            }
            logger.warn("Memory warning: {}MB / {}MB ({:.1f}%)",
                    used / (1024 * 1024), max / (1024 * 1024), usageRatio * 100);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Memory: {}MB / {}MB ({:.1f}%)",
                    used / (1024 * 1024), max / (1024 * 1024), usageRatio * 100);
        }
    }

    /**
     * 触发垃圾回收。
     * 在内存严重不足时调用。
     */
    private void triggerGC() {
        logger.info("Triggering garbage collection");
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 获取当前内存统计信息。
     *
     * @return 内存统计信息
     */
    public MemoryStats getStats() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        if (max <= 0) {
            max = runtime.maxMemory();
        }
        long committed = heapUsage.getCommitted();

        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        long nonHeapUsed = nonHeapUsage.getUsed();

        return new MemoryStats(
                used,
                committed,
                max,
                nonHeapUsed,
                runtime.totalMemory(),
                runtime.freeMemory(),
                runtime.availableProcessors()
        );
    }

    /**
     * 设置内存警告监听器。
     * 当内存使用超过警告阈值（85%）时被调用。
     *
     * @param listener 警告监听器
     */
    public void setWarningListener(MemoryWarningListener listener) {
        this.warningListener = listener;
    }

    /**
     * 设置内存严重警告监听器。
     * 当内存使用超过关键阈值（95%）时被调用。
     *
     * @param listener 严重警告监听器
     */
    public void setCriticalListener(MemoryCriticalListener listener) {
        this.criticalListener = listener;
    }

    /**
     * 关闭内存监控器。
     * 停止定时任务并释放资源。
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
        logger.info("MemoryMonitor shutdown");
    }

    @FunctionalInterface
    public interface MemoryWarningListener {
        void onWarning(long used, long max, double ratio);
    }

    @FunctionalInterface
    public interface MemoryCriticalListener {
        void onCritical(long used, long max, double ratio);
    }

    public record MemoryStats(
            long heapUsed,
            long heapCommitted,
            long heapMax,
            long nonHeapUsed,
            long totalMemory,
            long freeMemory,
            int availableProcessors
    ) {
        public double heapUsageRatio() {
            return heapMax > 0 ? (double) heapUsed / heapMax : 0;
        }

        public String heapUsedMB() {
            return formatMB(heapUsed);
        }

        public String heapMaxMB() {
            return formatMB(heapMax);
        }

        public String heapCommittedMB() {
            return formatMB(heapCommitted);
        }

        private String formatMB(long bytes) {
            return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        }
    }
}
