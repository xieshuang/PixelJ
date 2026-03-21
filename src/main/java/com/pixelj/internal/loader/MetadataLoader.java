package com.pixelj.internal.loader;

import com.pixelj.internal.db.MetadataIndex;
import com.pixelj.spi.ImageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/**
 * 元数据批量加载器。
 * 负责后台异步读取图片的 EXIF 元数据，并缓存到数据库。
 */
public class MetadataLoader {

    private static final Logger logger = LoggerFactory.getLogger(MetadataLoader.class);

    private final MetadataIndex metadataIndex;
    private final ImageDecoder[] decoders;
    private final ExecutorService executor;
    private final Map<String, Map<Path, ImageDecoder.ImageMetadata>> directoryMetadataCache;

    public MetadataLoader(MetadataIndex metadataIndex) {
        this.metadataIndex = metadataIndex;
        this.decoders = loadDecoders();
        this.executor = Executors.newFixedThreadPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
                r -> {
                    Thread t = new Thread(r, "metadata-loader");
                    t.setPriority(Thread.MIN_PRIORITY);
                    t.setDaemon(true);
                    return t;
                }
        );
        this.directoryMetadataCache = new ConcurrentHashMap<>();
    }

    private ImageDecoder[] loadDecoders() {
        ServiceLoader<ImageDecoder> loader = ServiceLoader.load(ImageDecoder.class);
        List<ImageDecoder> decoderList = new ArrayList<>();
        for (ImageDecoder decoder : loader) {
            decoderList.add(decoder);
            logger.info("Loaded decoder: {} for {}",
                    decoder.getClass().getSimpleName(),
                    Arrays.toString(decoder.supportedExtensions()));
        }
        decoderList.sort(Comparator.comparingInt(ImageDecoder::getPriority).reversed());
        return decoderList.toArray(new ImageDecoder[0]);
    }

    private Optional<ImageDecoder.ImageMetadata> readMetadata(Path path) {
        String extension = getExtension(path);
        for (ImageDecoder decoder : decoders) {
            if (Arrays.asList(decoder.supportedExtensions()).contains(extension.toLowerCase())) {
                try {
                    return decoder.readMetadata(path);
                } catch (Exception e) {
                    logger.warn("Decoder {} failed to read metadata: {}", decoder.getClass().getSimpleName(), path);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<java.awt.image.BufferedImage> decode(Path path) {
        String extension = getExtension(path);
        for (ImageDecoder decoder : decoders) {
            if (Arrays.asList(decoder.supportedExtensions()).contains(extension.toLowerCase())) {
                try {
                    return decoder.decode(path, ImageDecoder.DecodeOptions.DEFAULT);
                } catch (Exception e) {
                    logger.warn("Decoder {} failed to decode: {}", decoder.getClass().getSimpleName(), path);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 预加载目录的元数据。
     * 如果缓存有效则直接回调，否则后台读取后存入数据库。
     *
     * @param directory 目录路径
     * @param files 目录下的文件列表
     * @param onComplete 完成后回调
     */
    public void preloadAsync(Path directory, List<Path> files, Runnable onComplete) {
        String dirStr = directory.toString();

        Map<Path, Long> fileLastModifiedMap = new HashMap<>();
        for (Path file : files) {
            try {
                fileLastModifiedMap.put(file, Files.getLastModifiedTime(file).toMillis());
            } catch (Exception e) {
                logger.warn("Failed to get last modified time: {}", file, e);
            }
        }

        if (metadataIndex.isCacheValid(dirStr, fileLastModifiedMap)) {
            logger.info("Metadata cache hit for directory: {}", dirStr);
            refreshCache(directory);
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        logger.info("Metadata cache miss for directory: {}, loading {} files", dirStr, files.size());

        executor.submit(() -> {
            try {
                List<MetadataIndex.ImageRecord> records = batchReadMetadata(files, directory.toString());

                if (!records.isEmpty()) {
                    metadataIndex.insertOrUpdateBatch(records);
                    logger.info("Saved {} metadata records for directory: {}", records.size(), dirStr);
                }

                refreshCache(directory);

            } catch (Exception e) {
                logger.error("Failed to preload metadata for directory: {}", dirStr, e);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    /**
     * 刷新内存缓存。
     */
    private void refreshCache(Path directory) {
        String dirStr = directory.toString();
        Map<Path, ImageDecoder.ImageMetadata> metaMap = metadataIndex.getMetadataMap(dirStr);
        directoryMetadataCache.put(dirStr, metaMap);
    }

    /**
     * 获取目录的元数据映射。
     */
    public Map<Path, ImageDecoder.ImageMetadata> getMetadataMap(Path directory) {
        String dirStr = directory.toString();
        return directoryMetadataCache.computeIfAbsent(dirStr, k -> metadataIndex.getMetadataMap(dirStr));
    }

    /**
     * 批量读取元数据。
     */
    private List<MetadataIndex.ImageRecord> batchReadMetadata(List<Path> paths, String directory) {
        List<MetadataIndex.ImageRecord> records = new ArrayList<>();
        List<Future<Optional<MetadataIndex.ImageRecord>>> futures = new ArrayList<>();

        for (Path path : paths) {
            Future<Optional<MetadataIndex.ImageRecord>> future = executor.submit(() -> {
                return readMetadata(path, directory);
            });
            futures.add(future);
        }

        for (Future<Optional<MetadataIndex.ImageRecord>> future : futures) {
            try {
                Optional<MetadataIndex.ImageRecord> opt = future.get(30, TimeUnit.SECONDS);
                opt.ifPresent(records::add);
            } catch (Exception e) {
                logger.warn("Failed to read metadata", e);
            }
        }

        return records;
    }

    /**
     * 读取单个文件的元数据。
     */
    private Optional<MetadataIndex.ImageRecord> readMetadata(Path path, String directory) {
        try {
            java.io.File file = path.toFile();
            if (!file.exists() || !file.canRead()) {
                return Optional.empty();
            }

            Optional<ImageDecoder.ImageMetadata> metaOpt = readMetadata(path);

            ImageDecoder.ImageMetadata meta = metaOpt.orElse(null);

            String camera = meta != null ? meta.camera() : null;
            String lens = meta != null ? meta.lens() : null;
            String focalLength = meta != null ? meta.focalLength() : null;
            String aperture = meta != null ? meta.aperture() : null;
            String shutterSpeed = meta != null ? meta.shutterSpeed() : null;
            String iso = meta != null ? meta.iso() : null;
            long dateTaken = meta != null ? meta.dateTaken() : 0;

            int width = 0;
            int height = 0;

            Optional<java.awt.image.BufferedImage> imageOpt = decode(path);
            if (imageOpt.isPresent()) {
                java.awt.image.BufferedImage img = imageOpt.get();
                width = img.getWidth();
                height = img.getHeight();
            }

            MetadataIndex.ImageRecord record = new MetadataIndex.ImageRecord(
                    path.toString(),
                    path.getFileName().toString(),
                    directory,
                    getExtension(path),
                    file.length(),
                    file.lastModified(),
                    width,
                    height,
                    camera,
                    lens,
                    focalLength,
                    aperture,
                    shutterSpeed,
                    iso,
                    dateTaken
            );

            return Optional.of(record);

        } catch (Exception e) {
            logger.warn("Failed to read metadata for: {}", path, e);
            return Optional.empty();
        }
    }

    private String getExtension(Path path) {
        String name = path.getFileName().toString();
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            return name.substring(lastDot + 1).toLowerCase();
        }
        return "";
    }

    /**
     * 关闭加载器。
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
