# PixelJ

高性能个人电脑照片浏览器，基于 Java 17 + JavaFX 21 开发。

## 项目特性

### 核心架构
- **三级缓存系统**：L1 内存缓存（SoftReference + LRU）、L2 磁盘缓存（缩略图）、L3 原始图像
- **异步图像加载**：基于 PriorityBlockingQueue 的优先级队列，支持高/中/低三级优先级
- **虚拟化瀑布流布局**：仅渲染可见区域图像单元格，实现 60fps 流畅滚动
- **SPI 解码器架构**：通过 ServiceLoader 机制动态加载图像格式解码器

### 文件处理
- **NIO.2 目录扫描**：使用 FileVisitor 高效遍历目录树
- **WatchService 监控**：实时监听文件系统变化，自动更新图像列表
- **H2 内存数据库**：存储图像元数据，支持快速查询

### 性能优化
- **预取管理器**：滚动时预测性加载即将可见的图像
- **内存监控**：实时跟踪堆内存使用，阈值触发 GC
- **滚动性能跟踪**：监控 FPS 和掉帧情况

## 技术栈

| 组件 | 技术 |
|------|------|
| 语言 | Java 17 |
| UI 框架 | JavaFX 21 |
| 构建工具 | Maven |
| 内存数据库 | H2 Database |
| 日志框架 | SLF4J + Logback |
| 图像解码 | ImageIO (JPEG/PNG/WebP) |

## 项目结构

```
src/main/java/com/pixelj/
├── PixelJApplication.java          # 应用程序入口
├── spi/
│   └── ImageDecoder.java          # 解码器接口
├── internal/
│   ├── cache/                    # 三级缓存系统
│   │   ├── L1MemoryCache.java
│   │   ├── L2DiskCache.java
│   │   └── ImageCacheCoordinator.java
│   ├── loader/                   # 图像加载器
│   │   ├── PriorityImageLoader.java
│   │   ├── ImageLoadingThreadPool.java
│   │   └── PrefetchManager.java
│   ├── decoder/                  # SPI 解码器实现
│   │   ├── ImageDecoderService.java
│   │   ├── JpegImageDecoder.java
│   │   ├── PngImageDecoder.java
│   │   └── WebPImageDecoder.java
│   ├── fs/                        # 文件系统
│   │   ├── FileScanner.java
│   │   └── FileWatcher.java
│   └── db/
│       └── MetadataIndex.java    # H2 索引
├── ui/                           # UI 层
│   ├── MainView.java
│   ├── VirtualizedWaterfallPane.java
│   ├── ImageCell.java
│   └── PerformanceMonitor.java
└── util/                         # 工具类
    ├── AppConfig.java
    ├── MemoryMonitor.java
    └── ScrollPerformanceTracker.java
```

## 构建说明

### 环境要求
- JDK 17+
- Maven 3.6+

### 编译
```bash
mvn clean compile
```

### 运行测试
```bash
mvn test
```

### 打包运行
```bash
mvn package
java -jar target/pixelj-1.0-SNAPSHOT.jar
```

## 配置参数

通过系统属性配置：

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `pixelj.l1.cache.size` | 512 | L1 内存缓存大小 (MB) |
| `pixelj.l2.cache.size` | 1024 | L2 磁盘缓存大小 (MB) |
| `pixelj.thumbnail.width` | 400 | 缩略图宽度 |
| `pixelj.thumbnail.height` | 300 | 缩略图高度 |
| `pixelj.prefetch.rows` | 3 | 预取行数 |
| `pixelj.decoder.pool` | CPU核心数 | 解码器线程池大小 |

## 架构设计

### 三级缓存流程

```
请求图像
    │
    ▼
┌─────────────────────────────────────────┐
│         ImageCacheCoordinator           │
│           (缓存协调器)                   │
└─────────────────────────────────────────┘
    │
    ├──────────────────────────────┐
    ▼                              ▼
┌─────────┐                   ┌─────────┐
│L1内存缓存│                   │L2磁盘缓存│
│(SoftRef│                   │(缩略图) │
│ + LRU) │                   │         │
└─────────┘                   └─────────┘
    │                              │
    └──────────┬───────────────────┘
               ▼
        ┌──────────┐
        │ 原始图像 │
        │ (磁盘)   │
        └──────────┘
```

### 虚拟化渲染原理

1. **滚动位置监听**：AnimationTimer 监听 ScrollPane 的滚动值变化
2. **可见性计算**：根据滚动位置和视口高度计算可见行范围
3. **单元格复用**：超出可见范围的单元格回收图像引用，重新利用
4. **异步加载**：使用 PriorityImageLoader 异步加载图像

## 许可证

本项目仅供学习交流使用。
