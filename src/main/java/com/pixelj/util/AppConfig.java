package com.pixelj.util;

/**
 * 应用配置管理器。
 * 通过系统属性配置应用行为，包括缓存大小、缩略图尺寸、线程池大小等参数。
 * 采用单例模式，通过 System.getProperty() 读取配置。
 */
public class AppConfig {

    private static final String PRISM_ORDER = "prism.order";
    private static final String PRISM_FORCE_GPU = "prism.forceGPU";
    private static final String PRISM_VSYNC = "prism.vsync";

    private static volatile AppConfig instance;

    private final boolean hardwareAccelerationEnabled;
    private final boolean vsyncEnabled;
    private final int l1CacheMaxMemoryMB;
    private final int l2CacheMaxDiskMB;
    private final int thumbnailWidth;
    private final int thumbnailHeight;
    private final int prefetchRows;
    private final int decoderPoolSize;

    private AppConfig() {
        this.hardwareAccelerationEnabled = Boolean.parseBoolean(
                System.getProperty(PRISM_FORCE_GPU, "true"));
        this.vsyncEnabled = Boolean.parseBoolean(
                System.getProperty(PRISM_VSYNC, "true"));
        this.l1CacheMaxMemoryMB = Integer.parseInt(
                System.getProperty("pixelj.l1.cache.size", "512"));
        this.l2CacheMaxDiskMB = Integer.parseInt(
                System.getProperty("pixelj.l2.cache.size", "1024"));
        this.thumbnailWidth = Integer.parseInt(
                System.getProperty("pixelj.thumbnail.width", "400"));
        this.thumbnailHeight = Integer.parseInt(
                System.getProperty("pixelj.thumbnail.height", "300"));
        this.prefetchRows = Integer.parseInt(
                System.getProperty("pixelj.prefetch.rows", "3"));
        this.decoderPoolSize = Integer.parseInt(
                System.getProperty("pixelj.decoder.pool",
                        String.valueOf(Runtime.getRuntime().availableProcessors())));
    }

    /**
     * 获取 AppConfig 单例实例。
     * 采用双重检查锁定确保线程安全。
     *
     * @return AppConfig 实例
     */
    public static AppConfig getInstance() {
        if (instance == null) {
            synchronized (AppConfig.class) {
                if (instance == null) {
                    instance = new AppConfig();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化应用配置。
     * 设置 JavaFX Prism 渲染参数和 GPU 加速配置。
     */
    public static void initialize() {
        System.setProperty(PRISM_ORDER, "d3d,sw");
        System.setProperty(PRISM_FORCE_GPU, "true");
        System.setProperty(PRISM_VSYNC, "true");

        getInstance();
    }

    /**
     * 检查硬件加速是否启用。
     *
     * @return 硬件加速启用状态
     */
    public boolean isHardwareAccelerationEnabled() {
        return hardwareAccelerationEnabled;
    }

    /**
     * 检查垂直同步是否启用。
     *
     * @return 垂直同步启用状态
     */
    public boolean isVsyncEnabled() {
        return vsyncEnabled;
    }

    /**
     * 获取 L1 内存缓存最大容量（MB）。
     *
     * @return 缓存容量（MB）
     */
    public int getL1CacheMaxMemoryMB() {
        return l1CacheMaxMemoryMB;
    }

    /**
     * 获取 L1 内存缓存最大容量（字节）。
     *
     * @return 缓存容量（字节）
     */
    public long getL1CacheMaxMemoryBytes() {
        return (long) l1CacheMaxMemoryMB * 1024 * 1024;
    }

    /**
     * 获取 L2 磁盘缓存最大容量（MB）。
     *
     * @return 缓存容量（MB）
     */
    public int getL2CacheMaxDiskMB() {
        return l2CacheMaxDiskMB;
    }

    /**
     * 获取 L2 磁盘缓存最大容量（字节）。
     *
     * @return 缓存容量（字节）
     */
    public long getL2CacheMaxDiskBytes() {
        return (long) l2CacheMaxDiskMB * 1024 * 1024;
    }

    /**
     * 获取缩略图默认宽度。
     *
     * @return 缩略图宽度
     */
    public int getThumbnailWidth() {
        return thumbnailWidth;
    }

    /**
     * 获取缩略图默认高度。
     *
     * @return 缩略图高度
     */
    public int getThumbnailHeight() {
        return thumbnailHeight;
    }

    /**
     * 获取预取行数。
     *
     * @return 预取行数
     */
    public int getPrefetchRows() {
        return prefetchRows;
    }

    /**
     * 获取解码器线程池大小。
     *
     * @return 线程池大小
     */
    public int getDecoderPoolSize() {
        return decoderPoolSize;
    }

    /**
     * 获取 Prism 渲染顺序配置。
     *
     * @return 渲染顺序字符串
     */
    public String getPrismOrder() {
        return System.getProperty(PRISM_ORDER, "d3d,sw");
    }

    @Override
    public String toString() {
        return "AppConfig{" +
                "hardwareAcceleration=" + hardwareAccelerationEnabled +
                ", vsync=" + vsyncEnabled +
                ", l1Cache=" + l1CacheMaxMemoryMB + "MB" +
                ", l2Cache=" + l2CacheMaxDiskMB + "MB" +
                ", thumbnail=" + thumbnailWidth + "x" + thumbnailHeight +
                ", prefetchRows=" + prefetchRows +
                ", decoderPool=" + decoderPoolSize +
                '}';
    }
}