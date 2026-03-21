package com.pixelj.internal.fs;

import com.pixelj.internal.decoder.ImageDecoderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件扫描器。
 * 使用 NIO.2 的 FileVisitor 遍历目录，查找支持的图像文件。
 */
public class FileScanner {

    private static final Logger logger = LoggerFactory.getLogger(FileScanner.class);

    private final ImageDecoderService decoderService;

    public FileScanner() {
        this.decoderService = ImageDecoderService.getInstance();
    }

    /**
     * 扫描指定目录及其子目录，查找所有支持的图像文件。
     *
     * @param rootDirectory 根目录路径
     * @return 图像文件路径列表
     * @throws IOException IO异常
     */
    public List<Path> scanDirectory(Path rootDirectory) throws IOException {
        List<Path> imageFiles = new ArrayList<>();
        AtomicInteger progress = new AtomicInteger(0);

        logger.info("Starting scan of directory: {}", rootDirectory);

        Files.walkFileTree(rootDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && isImageFile(file)) {
                    imageFiles.add(file);
                    int count = progress.incrementAndGet();
                    if (count % 100 == 0) {
                        logger.debug("Scanned {} files", count);
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                logger.warn("Failed to access: {}", file);
                return FileVisitResult.CONTINUE;
            }
        });

        logger.info("Scan complete: found {} image files", imageFiles.size());
        return imageFiles;
    }

    /**
     * 检查给定路径是否为支持的图像文件。
     *
     * @param file 文件路径
     * @return 是否为支持的图像文件
     */
    public boolean isImageFile(Path file) {
        String extension = getExtension(file);
        return decoderService.isSupported(extension);
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot > 0 ? name.substring(lastDot + 1).toLowerCase() : "";
    }
}
