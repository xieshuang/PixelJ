# PixelJ 开发指南

## 项目概述

PixelJ 是一个高性能的个人电脑照片浏览器，基于 Java 17、JavaFX 21 和 Maven 构建。

## 构建命令

### Maven 操作

```bash
# 清理并编译
mvn clean compile

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=AppConfigTest
mvn test -Dtest=L1MemoryCacheTest
mvn test -Dtest=ImageDecoderTest

# 运行单个测试方法
mvn test -Dtest=AppConfigTest#testSingleton

# 打包 JAR
mvn package

# 运行应用程序（需要 JavaFX）
mvn javafx:run
```

### 环境要求

- JDK 17+
- Maven 3.6+
- JavaFX SDK 21（用于 javafx:run）

## 代码风格指南

### Java 规范

- **缩进**：4 个空格（不使用 Tab）
- **行长度**：无硬性限制，但建议 < 120 字符
- **编码**：UTF-8
- **换行符**：LF（Windows 上可能会自动转换为 CRLF）

### 命名规范

| 元素  | 规范          | 示例                                    |
| --- | ----------- | ------------------------------------- |
| 类名  | PascalCase  | `ImageCacheCoordinator`               |
| 方法名 | camelCase   | `submitImage`, `getStats`             |
| 变量名 | camelCase   | `cacheCoordinator`, `imageFiles`      |
| 常量  | UPPER_SNAKE | `MAX_CACHE_SIZE`, `WARNING_THRESHOLD` |
| 包名  | 小写          | `com.pixelj.internal.cache`           |

### 导入组织

1. `java.*` 标准库
2. `javax.*` 扩展
3. 第三方库（`org.*`, `com.*`）
4. 内部包（`com.pixelj.*`）
5. 各组之间空行分隔，不允许使用通配符导入

### 类型与变量

- 尽可能使用接口类型：`List<Path>` 而非 `ArrayList<Path>`
- 不可变数据使用 final 字段：`private final String path`
- 简单值使用基本类型
- 使用 Record 类作为不可变数据载体（参见 `ImageRecord`, `PrefetchStats`）

### 异常处理

- 可恢复的错误条件使用受检异常
- 记录错误时附带上下文信息：`logger.error("Failed to load: {}", path, e)`
- 绝不静默吞掉异常
- 使用 finally 块或 try-with-resources 关闭资源

### 文档

- 所有公开 API 必须包含 JavaDoc
- 适当包含 `@param`、`@return`、`@throws`
- 用户面向的文本和业务逻辑使用中文注释
- 代码元素使用英文技术术语

## 项目结构

```
src/main/java/com/pixelj/
├── PixelJApplication.java          # 入口点
├── spi/                           # SPI 接口
│   └── ImageDecoder.java
├── internal/                      # 内部实现
│   ├── cache/                     # L1/L2/L3 三级缓存系统
│   ├── decoder/                   # 图片解码器（JPEG/PNG/WebP）
│   ├── fs/                        # 文件扫描与监听
│   ├── loader/                    # 异步加载与预取
│   └── db/                        # H2 元数据索引
├── ui/                            # JavaFX UI 组件
│   ├── MainView.java
│   ├── VirtualizedWaterfallPane.java
│   ├── ImageCell.java
│   └── PerformanceMonitor.java
└── util/                          # 工具类与配置
    ├── AppConfig.java
    ├── MemoryMonitor.java
    └── ScrollPerformanceTracker.java

src/test/java/com/pixelj/          # 单元测试
```

## 架构原则

### 三级缓存

1. **L1**：SoftReference 内存缓存（LRU 策略，默认 512MB）
2. **L2**：缩略图磁盘缓存（默认 1GB）
3. **L3**：文件系统中的原始图片

### 虚拟化渲染

- 只渲染可见单元格 + 缓冲区行
- 滚动出视野时回收单元格
- 使用 AnimationTimer 实现平滑滚动

### SPI 解码器模式

- 实现 `ImageDecoder` 接口
- 在 `META-INF/services/com.pixelj.spi.ImageDecoder` 注册
- 支持优先级解码与降级方案

### 异步加载

- 使用 PriorityBlockingQueue 调度任务
- HIGH/MEDIUM/LOW 三种优先级
- 使用 CompletableFuture 处理异步结果

## 测试指南

- 实际可行时，每个测试一个断言
- 测试命名：`方法名_预期行为`
- 使用 @BeforeEach 进行公共设置
- 适当模拟外部依赖

## Git 工作流程

- 提交信息：`type: description` 格式（feat/fix/chore/docs）
- 分支命名：`feature/`、`fix/`、`refactor/`
- 提交前运行测试
- 不提交未跟踪的文件（使用 .gitignore）
