package com.pixelj.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 滚动性能跟踪器。
 * 跟踪滚动帧率、掉帧情况等性能指标，用于性能监控和优化。
 */
public class ScrollPerformanceTracker {

    private static final Logger logger = LoggerFactory.getLogger(ScrollPerformanceTracker.class);

    private final AtomicLong frameCount;
    private final AtomicLong dropCount;
    private final AtomicLong totalScrollTime;
    private final AtomicLong lastFrameTime;
    private final AtomicLong fps;

    private volatile boolean enabled;
    private long startTime;
    private long lastReportTime;

    public ScrollPerformanceTracker() {
        this.frameCount = new AtomicLong(0);
        this.dropCount = new AtomicLong(0);
        this.totalScrollTime = new AtomicLong(0);
        this.lastFrameTime = new AtomicLong(System.nanoTime());
        this.fps = new AtomicLong(60);
        this.enabled = false;
        this.startTime = System.currentTimeMillis();
        this.lastReportTime = startTime;
    }

    /**
     * 标记帧开始。
     * 记录帧时间戳并检测是否掉帧（帧时间超过20ms）。
     */
    public void beginFrame() {
        if (!enabled) return;

        long now = System.nanoTime();
        long elapsed = now - lastFrameTime.getAndSet(now);

        if (elapsed > 20_000_000) {
            dropCount.incrementAndGet();
        }

        frameCount.incrementAndGet();
        totalScrollTime.addAndGet(elapsed);
    }

    /**
     * 标记滚动结束。
     * 累加最后一帧的耗时。
     */
    public void endScroll() {
        if (!enabled) return;

        long now = System.nanoTime();
        long elapsed = now - lastFrameTime.get();
        totalScrollTime.addAndGet(elapsed);
    }

    public void startTracking() {
        enabled = true;
        startTime = System.currentTimeMillis();
        lastReportTime = startTime;
        frameCount.set(0);
        dropCount.set(0);
        totalScrollTime.set(0);
        logger.info("Scroll performance tracking started");
    }

    /**
     * 停止跟踪并重置统计。
     */
    public void stopTracking() {
        enabled = false;
        logger.info("Scroll performance tracking stopped");
    }

    public void report() {
        if (!enabled) return;

        long now = System.currentTimeMillis();
        long duration = now - lastReportTime;

        if (duration < 1000) return;

        long frames = frameCount.get();
        long drops = dropCount.get();
        long totalNs = totalScrollTime.get();

        double avgFrameTime = frames > 0 ? (double) totalNs / frames / 1_000_000 : 0;
        double currentFps = frames > 0 ? (frames * 1000.0) / duration : 0;
        double dropRate = frames > 0 ? (double) drops / frames * 100 : 0;

        fps.set((long) currentFps);

        logger.info("Scroll Stats: fps={}, avgFrame={:.2f}ms, drops={} ({:.1f}%), duration={}ms",
                (long) currentFps, avgFrameTime, drops, dropRate, duration);

        lastReportTime = now;
        frameCount.set(0);
        dropCount.set(0);
        totalScrollTime.set(0);
    }

    /**
     * 获取当前 FPS。
     *
     * @return FPS 值
     */
    public long getFps() {
        return fps.get();
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 滚动平均值计算器。
     * 维护固定大小的窗口，计算最近N个值的统计信息。
     */
    public static class RollingAverage {
        private static final int WINDOW_SIZE = 60;
        private final double[] values;
        private int index;
        private int count;

        public RollingAverage() {
            this.values = new double[WINDOW_SIZE];
            this.index = 0;
            this.count = 0;
        }

        public void add(double value) {
            values[index] = value;
            index = (index + 1) % WINDOW_SIZE;
            if (count < WINDOW_SIZE) {
                count++;
            }
        }

        public double getAverage() {
            if (count == 0) return 0;
            double sum = 0;
            for (int i = 0; i < count; i++) {
                sum += values[i];
            }
            return sum / count;
        }

        public double getMin() {
            if (count == 0) return 0;
            double min = Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                min = Math.min(min, values[i]);
            }
            return min;
        }

        public double getMax() {
            if (count == 0) return 0;
            double max = Double.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, values[i]);
            }
            return max;
        }
    }
}
