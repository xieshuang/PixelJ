package com.pixelj.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史记录管理器。
 * 负责存储和加载用户最近打开的文件夹历史记录。
 */
public class HistoryManager {

    private static final Logger logger = LoggerFactory.getLogger(HistoryManager.class);
    private static final int MAX_HISTORY_SIZE = 10;
    private static final String HISTORY_FILE = ".pixelj_history";

    private static volatile HistoryManager instance;
    private final Path historyFilePath;
    private List<HistoryItem> historyItems;

    private HistoryManager() {
        this.historyFilePath = Paths.get(System.getProperty("user.home"), HISTORY_FILE);
        this.historyItems = loadHistory();
    }

    /**
     * 获取 HistoryManager 单例实例。
     *
     * @return HistoryManager 实例
     */
    public static HistoryManager getInstance() {
        if (instance == null) {
            synchronized (HistoryManager.class) {
                if (instance == null) {
                    instance = new HistoryManager();
                }
            }
        }
        return instance;
    }

    /**
     * 加载历史记录。
     *
     * @return 历史记录列表
     */
    private List<HistoryItem> loadHistory() {
        if (!Files.exists(historyFilePath)) {
            return new ArrayList<>();
        }
        try {
            List<String> lines = Files.readAllLines(historyFilePath);
            return lines.stream()
                    .map(line -> line.trim())
                    .filter(line -> !line.isEmpty())
                    .map(Path::of)
                    .filter(Files::exists)
                    .map(HistoryItem::new)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.warn("Failed to load history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 保存历史记录到文件。
     */
    private void saveHistory() {
        try {
            Files.createDirectories(historyFilePath.getParent());
            List<String> lines = historyItems.stream()
                    .map(item -> item.path.toString())
                    .collect(Collectors.toList());
            Files.writeString(historyFilePath, String.join("\n", lines));
        } catch (IOException e) {
            logger.error("Failed to save history", e);
        }
    }

    /**
     * 添加历史记录。
     *
     * @param path 文件夹路径
     */
    public void addHistory(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return;
        }

        historyItems.removeIf(item -> item.path.equals(path));

        historyItems.add(0, new HistoryItem(path));

        if (historyItems.size() > MAX_HISTORY_SIZE) {
            historyItems = new ArrayList<>(historyItems.subList(0, MAX_HISTORY_SIZE));
        }

        saveHistory();
    }

    /**
     * 获取历史记录列表。
     *
     * @return 只读的历史记录列表
     */
    public List<HistoryItem> getHistory() {
        return Collections.unmodifiableList(historyItems);
    }

    /**
     * 清除所有历史记录。
     */
    public void clearHistory() {
        historyItems.clear();
        saveHistory();
    }

    /**
     * 移除指定的历史记录。
     *
     * @param path 要移除的路径
     */
    public void removeHistory(Path path) {
        historyItems.removeIf(item -> item.path.equals(path));
        saveHistory();
    }

    /**
     * 历史记录项。
     */
    public static class HistoryItem {
        private final Path path;

        public HistoryItem(Path path) {
            this.path = path;
        }

        public Path getPath() {
            return path;
        }

        public String getFileName() {
            return path != null ? path.getFileName().toString() : "";
        }

        public String getParentPath() {
            return path != null ? path.getParent().toString() : "";
        }
    }
}
