package com.pixelj.internal.loader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图像加载线程池管理器，提供可配置的线程池用于图像解码任务。
 * 支持创建单一线程池和优先级分离的线程池组。
 * 线程池中的线程设置为守护线程，并采用低优先级以避免影响UI响应。
 */
public class ImageLoadingThreadPool {

    private static final Logger logger = LoggerFactory.getLogger(ImageLoadingThreadPool.class);

    /**
     * 创建图像加载线程池。
     *
     * @param poolName       线程池名称，用于线程命名
     * @param coreSize       核心线程数
     * @param maxSize        最大线程数
     * @param queueCapacity  任务队列容量
     * @return 配置好的 ExecutorService 实例
     */
    public static ExecutorService createImagePool(String poolName, int coreSize, int maxSize, int queueCapacity) {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, poolName + "-" + counter.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY + 2);
                return t;
            }
        };

        RejectedExecutionHandler handler = (r, executor) -> {
            if (maxSize > coreSize) {
                Thread t = factory.newThread(r);
                t.start();
            } else {
                throw new RejectedExecutionException("Task rejected");
            }
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                factory,
                handler
        );

        executor.allowCoreThreadTimeOut(true);
        logger.info("Created image pool: name={}, core={}, max={}, queue={}",
                poolName, coreSize, maxSize, queueCapacity);
        return executor;
    }

    /**
     * 创建推荐配置的图像加载线程池。
     * 线程池大小根据CPU核心数自动确定。
     *
     * @return 推荐配置的 ExecutorService 实例
     */
    public static ExecutorService createRecommendedPool() {
        int cores = Runtime.getRuntime().availableProcessors();
        return createImagePool("image-decoder", cores, cores * 2, 500);
    }

    public static class PrioritySeparatedPool {
        private final ExecutorService highPriorityPool;
        private final ExecutorService normalPriorityPool;
        private final ExecutorService lowPriorityPool;

        public PrioritySeparatedPool() {
            int cores = Runtime.getRuntime().availableProcessors();

            this.highPriorityPool = createImagePool("image-high", cores, cores * 2, 200);
            this.normalPriorityPool = createImagePool("image-normal", cores / 2 + 1, cores, 500);
            this.lowPriorityPool = createImagePool("image-low", 1, cores / 2, 1000);

            logger.info("PrioritySeparatedPool initialized");
        }

        /**
         * 获取高优先级线程池。
         *
         * @return 高优先级 ExecutorService
         */
        public ExecutorService getHighPriorityPool() {
            return highPriorityPool;
        }

        public ExecutorService getNormalPriorityPool() {
            return normalPriorityPool;
        }

        /**
         * 获取低优先级线程池。
         *
         * @return 低优先级 ExecutorService
         */
        public ExecutorService getLowPriorityPool() {
            return lowPriorityPool;
        }

        /**
         * 根据优先级获取对应的线程池。
         *
         * @param priority 优先级级别
         * @return 对应优先级的 ExecutorService
         */
        public ExecutorService getPoolForPriority(PriorityImageLoader.Priority priority) {
            return switch (priority) {
                case HIGH -> highPriorityPool;
                case MEDIUM -> normalPriorityPool;
                case LOW -> lowPriorityPool;
            };
        }

        /**
         * 关闭所有优先级线程池。
         * 等待最多5秒让任务完成，然后强制关闭。
         */
        public void shutdown() {
            highPriorityPool.shutdown();
            normalPriorityPool.shutdown();
            lowPriorityPool.shutdown();
            try {
                if (!highPriorityPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    highPriorityPool.shutdownNow();
                }
                if (!normalPriorityPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    normalPriorityPool.shutdownNow();
                }
                if (!lowPriorityPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    lowPriorityPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                highPriorityPool.shutdownNow();
                normalPriorityPool.shutdownNow();
                lowPriorityPool.shutdownNow();
            }
        }
    }
}
