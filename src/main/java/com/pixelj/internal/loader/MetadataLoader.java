package com.pixelj.internal.loader;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.pixelj.internal.db.MetadataIndex;
import com.pixelj.util.GeoCoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 元数据批量加载器。
 * 负责异步批量读取图片的 EXIF 元数据并存入数据库。
 */
public class MetadataLoader {

    private static final Logger logger = LoggerFactory.getLogger(MetadataLoader.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final int BATCH_SIZE = 50;
    private static final int MAX_THREADS = 4;

    private final MetadataIndex metadataIndex;
    private final GeoCoder geoCoder;
    private final ExecutorService executor;

    public MetadataLoader(MetadataIndex metadataIndex) {
        this.metadataIndex = metadataIndex;
        this.geoCoder = new GeoCoder();
        this.executor = Executors.newFixedThreadPool(MAX_THREADS, r -> {
            Thread t = new Thread(r, "metadata-loader-" + System.nanoTime());
            t.setPriority(Thread.MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 预加载目录下所有图片的元数据。
     *
     * @param directory  目录路径
     * @param paths      图片路径列表
     * @param onComplete 完成回调
     */
    public void preloadDirectory(Path directory, List<Path> paths, Runnable onComplete) {
        executor.submit(() -> {
            try {
                Map<String, Long> fileLastModified = new HashMap<>();
                for (Path p : paths) {
                    try {
                        fileLastModified.put(p.toString(), Files.getLastModifiedTime(p).toMillis());
                    } catch (Exception e) {
                        logger.warn("Failed to get last modified: {}", p);
                    }
                }

                if (metadataIndex.isCacheValid(directory.toString(), fileLastModified)) {
                    logger.info("Cache valid for directory: {}", directory);
                } else {
                    logger.info("Cache invalid, reloading metadata for {} files", paths.size());
                    List<MetadataIndex.ImageRecord> records = batchReadMetadata(paths);
                    metadataIndex.saveImageRecords(records);
                    logger.info("Saved {} metadata records", records.size());
                }

                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception e) {
                logger.error("Failed to preload metadata", e);
            }
        });
    }

    /**
     * 批量读取元数据。
     */
    private List<MetadataIndex.ImageRecord> batchReadMetadata(List<Path> paths) {
        List<List<Path>> batches = partitionList(paths, BATCH_SIZE);
        List<CompletableFuture<List<MetadataIndex.ImageRecord>>> futures = new ArrayList<>();

        for (List<Path> batch : batches) {
            CompletableFuture<List<MetadataIndex.ImageRecord>> future = CompletableFuture.supplyAsync(
                    () -> processBatch(batch), executor);
            futures.add(future);
        }

        return futures.stream()
                .flatMap(f -> {
                    try {
                        return f.join().stream();
                    } catch (Exception e) {
                        logger.error("Batch processing failed", e);
                        return Collections.<MetadataIndex.ImageRecord>emptyList().stream();
                    }
                })
                .collect(Collectors.toList());
    }

    private List<List<Path>> partitionList(List<Path> list, int size) {
        List<List<Path>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    private List<MetadataIndex.ImageRecord> processBatch(List<Path> paths) {
        List<MetadataIndex.ImageRecord> records = new ArrayList<>();
        for (Path path : paths) {
            try {
                MetadataIndex.ImageRecord record = readMetadata(path);
                if (record != null) {
                    records.add(record);
                }
            } catch (Exception e) {
                logger.debug("Failed to read metadata: {}", path);
            }
        }
        return records;
    }

    private MetadataIndex.ImageRecord readMetadata(Path path) {
        File file = path.toFile();
        if (!file.exists()) {
            return null;
        }

        String camera = null;
        String lens = null;
        String focalLength = null;
        String aperture = null;
        String shutterSpeed = null;
        String iso = null;
        long dateTaken = 0;
        double latitude = 0;
        double longitude = 0;
        String locationName = null;

        try {
            Metadata metadata = JpegMetadataReader.readMetadata(file);

            ExifIFD0Directory exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0 != null) {
                camera = exifIFD0.getDescription(ExifIFD0Directory.TAG_MODEL);
            }

            ExifSubIFDDirectory exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifSubIFD != null) {
                lens = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_LENS_MODEL);
                focalLength = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                aperture = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_APERTURE);
                shutterSpeed = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_EXPOSURE_TIME);
                iso = exifSubIFD.getDescription(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT);

                Date date = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL, TimeZone.getDefault());
                if (date != null) {
                    dateTaken = date.getTime();
                } else {
                    date = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME, TimeZone.getDefault());
                    if (date != null) {
                        dateTaken = date.getTime();
                    }
                }
            }

            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                com.drew.lang.GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null) {
                    latitude = geoLocation.getLatitude();
                    longitude = geoLocation.getLongitude();
                    locationName = geoCoder.reverseGeocode(latitude, longitude);
                }
            }
        } catch (Exception e) {
            logger.debug("Error reading metadata: {}", path);
        }

        String parentDir = path.getParent() != null ? path.getParent().toString() : "";
        String filename = path.getFileName() != null ? path.getFileName().toString() : "";
        String extension = "";
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            extension = filename.substring(dotIndex + 1).toLowerCase();
        }

        return new MetadataIndex.ImageRecord(
                path.toString(),
                filename,
                parentDir,
                extension,
                file.length(),
                file.lastModified(),
                0, 0,
                camera,
                lens,
                focalLength,
                aperture,
                shutterSpeed,
                iso,
                dateTaken,
                latitude,
                longitude,
                locationName
        );
    }

    /**
     * 获取目录下所有图片的元数据映射。
     */
    public Map<Path, MetadataIndex.ImageRecord> getMetadataMap(Path directory) {
        Map<String, MetadataIndex.ImageRecord> dbMap = metadataIndex.getMetadataMapByDirectory(directory.toString());
        Map<Path, MetadataIndex.ImageRecord> result = new HashMap<>();
        for (Map.Entry<String, MetadataIndex.ImageRecord> entry : dbMap.entrySet()) {
            result.put(Path.of(entry.getKey()), entry.getValue());
        }
        return result;
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
