package com.pixelj.internal.fs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 文件系统监听器
 * 
 * <p>使用JDK的WatchService API监控目录变化，
 * 支持文件的增删改事件。
 * 
 * <p>特性：
 * <ul>
 *   <li>自动递归监听子目录</li>
 *   <li>仅监听支持的图片格式</li>
 *   <li>单线程处理事件，避免并发问题</li>
 * </ul>
 * 
 * @author PixelJ Team
 */
public class FileWatcher {

    private static final Logger logger = LoggerFactory.getLogger(FileWatcher.class);

    private final Path watchedDirectory;
    private final FileScanner fileScanner;
    private final WatchService watchService;
    private final ExecutorService executor;
    private final Map<WatchKey, Path> watchKeys;
    private volatile boolean running;

    private Consumer<Path> onFileAdded;
    private Consumer<Path> onFileRemoved;
    private Consumer<Path> onFileModified;

    /**
     * 创建文件监听器
     * 
     * @param directory 要监听的目录
     * @throws IOException 如果创建WatchService失败
     */
    public FileWatcher(Path directory) throws IOException {
        this.watchedDirectory = directory;
        this.fileScanner = new FileScanner();
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "file-watcher");
            t.setDaemon(true);
            return t;
        });
        this.watchKeys = new HashMap<>();
        this.running = false;
    }

    /**
     * 启动监听
     * 
     * <p>注册根目录并启动事件处理循环。
     */
    public void start() {
        if (running) return;
        running = true;

        registerDirectory(watchedDirectory);
        executor.submit(this::watchLoop);
        logger.info("FileWatcher started for: {}", watchedDirectory);
    }

    /**
     * 停止监听
     */
    public void stop() {
        running = false;
        executor.shutdown();
        try {
            watchService.close();
        } catch (IOException e) {
            logger.error("Error closing watch service", e);
        }
        logger.info("FileWatcher stopped");
    }

    /**
     * 注册目录监听
     * 
     * <p>递归注册所有子目录。
     */
    private void registerDirectory(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            watchKeys.put(key, dir);
            logger.debug("Registered directory for watching: {}", dir);
        } catch (IOException e) {
            logger.error("Failed to register directory: {}", dir, e);
        }
    }

    /**
     * 事件处理循环
     * 
     * <p>处理流程：
     * <ol>
     *   <li>阻塞等待事件</li>
     *   <li>遍历所有发生的事件</li>
     *   <li>根据事件类型触发对应回调</li>
     *   <li>新目录自动注册监听</li>
     * </ol>
     */
    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                return;
            }

            Path dir = watchKeys.get(key);
            if (dir == null) continue;

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                // OVERFLOW表示事件过多，跳过
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                Path fileName = pathEvent.context();
                Path fullPath = dir.resolve(fileName);

                // 文件创建
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(fullPath)) {
                        // 新目录也要监听
                        registerDirectory(fullPath);
                    } else if (fileScanner.isImageFile(fullPath) && onFileAdded != null) {
                        logger.debug("File added: {}", fullPath);
                        onFileAdded.accept(fullPath);
                    }
                } 
                // 文件删除
                else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    if (onFileRemoved != null) {
                        onFileRemoved.accept(fullPath);
                    }
                } 
                // 文件修改
                else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (fileScanner.isImageFile(fullPath) && onFileModified != null) {
                        onFileModified.accept(fullPath);
                    }
                }
            }

            key.reset();
        }
    }

    /**
     * 设置文件添加回调
     */
    public void setOnFileAdded(Consumer<Path> callback) {
        this.onFileAdded = callback;
    }

    /**
     * 设置文件删除回调
     */
    public void setOnFileRemoved(Consumer<Path> callback) {
        this.onFileRemoved = callback;
    }

    /**
     * 设置文件修改回调
     */
    public void setOnFileModified(Consumer<Path> callback) {
        this.onFileModified = callback;
    }

    /**
     * 获取监听的目录
     */
    public Path getWatchedDirectory() {
        return watchedDirectory;
    }
}
