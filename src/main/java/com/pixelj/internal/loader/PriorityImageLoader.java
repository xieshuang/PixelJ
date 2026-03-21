package com.pixelj.internal.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优先级图片加载器
 * 
 * <p>特性：
 * <ul>
 *   <li>基于PriorityBlockingQueue的优先级队列</li>
 *   <li>HIGH优先级任务优先加载</li>
 *   <li>同一路径的重复请求会合并</li>
 *   <li>低优先级任务可被取消</li>
 * </ul>
 * 
 * <p>优先级定义：
 * <ul>
 *   <li>HIGH - 当前屏幕可见区域</li>
 *   <li>MEDIUM - 预取区域</li>
 *   <li>LOW - 后台任务</li>
 * </ul>
 * 
 * @author PixelJ Team
 */
public class PriorityImageLoader {

    private static final Logger logger = LoggerFactory.getLogger(PriorityImageLoader.class);

    /**
     * 加载优先级
     */
    public enum Priority {
        /** 高优先级：屏幕可见区域 */
        HIGH(3),
        /** 中优先级：预取区域 */
        MEDIUM(2),
        /** 低优先级：后台任务 */
        LOW(1);

        private final int level;

        Priority(int level) {
            this.level = level;
        }

        public int getLevel() {
            return level;
        }
    }

    /**
     * 图片加载任务
     * 
     * <p>实现Comparable接口以支持优先级队列排序。
     * 排序规则：优先级高的在前，相同优先级时按提交时间排序。
     */
    private static class ImageLoadTask implements Comparable<ImageLoadTask> {
        final Path path;
        final Priority priority;
        final CompletableFuture<BufferedImage> future;
        final long submitTime;
        final long sequence;

        private static final AtomicLong seqGenerator = new AtomicLong(0);

        ImageLoadTask(Path path, Priority priority) {
            this.path = path;
            this.priority = priority;
            this.future = new CompletableFuture<>();
            this.submitTime = System.nanoTime();
            this.sequence = seqGenerator.getAndIncrement();
        }

        @Override
        public int compareTo(ImageLoadTask other) {
            // 优先级高的优先
            int cmp = Integer.compare(other.priority.getLevel(), this.priority.getLevel());
            if (cmp != 0) return cmp;
            // 相同优先级，按提交时间
            return Long.compare(this.submitTime, other.submitTime);
        }
    }

    private final ExecutorService executor;
    private final PriorityBlockingQueue<ImageLoadTask> taskQueue;
    /** 正在进行的任务，防止重复加载 */
    private final ConcurrentHashMap<Path, ImageLoadTask> pendingTasks;
    private final com.pixelj.internal.cache.ImageCacheCoordinator coordinator;
    private final ScheduledExecutorService scheduler;

    /**
     * 创建优先级图片加载器
     * 
     * @param coordinator 缓存协调器
     * @param poolSize 线程池大小
     */
    public PriorityImageLoader(com.pixelj.internal.cache.ImageCacheCoordinator coordinator, int poolSize) {
        this.coordinator = coordinator;
        this.taskQueue = new PriorityBlockingQueue<>(100);
        this.pendingTasks = new ConcurrentHashMap<>();

        this.executor = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "priority-image-loader-" + System.nanoTime());
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        });

        this.scheduler = Executors.newScheduledThreadPool(1);

        startWorkers(poolSize);
        startHealthCheck();

        logger.info("PriorityImageLoader initialized with poolSize={}", poolSize);
    }

    /**
     * 提交加载任务
     * 
     * <p>如果同一路径已有任务在执行：
     * <ul>
     *   <li>新优先级更高：取消旧任务，使用新任务</li>
     *   <li>新优先级更低或相同：返回已有的Future</li>
     * </ul>
     * 
     * @param path     图片路径
     * @param priority 优先级
     * @return 加载结果的Future
     */
    public CompletableFuture<BufferedImage> submit(Path path, Priority priority) {
        ImageLoadTask existingTask = pendingTasks.get(path);
        if (existingTask != null) {
            // 新优先级更高，取消低优先级任务
            if (priority.getLevel() > existingTask.priority.getLevel()) {
                existingTask.future.cancel(false);
                pendingTasks.remove(path);
            } else {
                return existingTask.future;
            }
        }

        ImageLoadTask task = new ImageLoadTask(path, priority);
        pendingTasks.put(path, task);
        taskQueue.offer(task);

        return task.future;
    }

    /**
     * 启动工作线程
     */
    private void startWorkers(int count) {
        for (int i = 0; i < count; i++) {
            executor.submit(this::workerLoop);
        }
    }

    /**
     * 工作线程主循环
     * 
     * <p>从优先级队列中取任务执行，
     * 完成后更新Future状态。
     */
    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                ImageLoadTask task = taskQueue.take();

                // 已完成或已取消的任务跳过
                if (task.future.isDone() || task.future.isCancelled()) {
                    pendingTasks.remove(task.path);
                    continue;
                }

                try {
                    BufferedImage image = coordinator.loadFromOriginal(task.path);
                    task.future.complete(image);
                } catch (Exception e) {
                    task.future.completeExceptionally(e);
                } finally {
                    pendingTasks.remove(task.path);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 启动健康检查
     * 
     * <p>每10秒检查一次队列长度，
     * 如果积压超过500个任务则发出警告。
     */
    private void startHealthCheck() {
        scheduler.scheduleAtFixedRate(() -> {
            int queueSize = taskQueue.size();
            if (queueSize > 500) {
                logger.warn("Image load queue backing up: {} tasks pending", queueSize);
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    /**
     * 取消指定路径的加载任务
     */
    public void cancel(Path path) {
        ImageLoadTask task = pendingTasks.get(path);
        if (task != null) {
            task.future.cancel(false);
            taskQueue.remove(task);
            pendingTasks.remove(path);
        }
    }

    /**
     * 取消所有低优先级任务
     */
    public void cancelLowPriority() {
        taskQueue.removeIf(task -> {
            if (task.priority == Priority.LOW) {
                task.future.cancel(false);
                pendingTasks.remove(task.path);
                return true;
            }
            return false;
        });
    }

    /**
     * 获取队列当前长度
     */
    public int getQueueSize() {
        return taskQueue.size();
    }

    /**
     * 关闭加载器
     */
    public void shutdown() {
        executor.shutdown();
        scheduler.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
