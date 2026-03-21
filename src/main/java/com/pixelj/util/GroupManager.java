package com.pixelj.util;

import com.pixelj.spi.ImageDecoder;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 图片分组管理器。
 * 支持按日期和地理位置对图片进行分组，类似手机相册功能。
 */
public class GroupManager {

    public enum GroupMode {
        NONE,
        BY_DATE,
        BY_LOCATION
    }

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月");
    private static final double LOCATION_CLUSTER_THRESHOLD = 0.01;

    private GroupMode currentMode = GroupMode.NONE;

    /**
     * 设置分组模式。
     *
     * @param mode 分组模式
     */
    public void setGroupMode(GroupMode mode) {
        this.currentMode = mode;
    }

    /**
     * 获取当前分组模式。
     *
     * @return 分组模式
     */
    public GroupMode getGroupMode() {
        return currentMode;
    }

    /**
     * 对图片列表进行分组。
     *
     * @param imagePaths 图片路径列表
     * @param metadataMap 元数据映射（路径 -> 元数据）
     * @return 分组后的列表
     */
    public List<GroupItem> groupImages(List<Path> imagePaths, Map<Path, ImageDecoder.ImageMetadata> metadataMap) {
        if (currentMode == GroupMode.NONE) {
            return imagePaths.stream()
                    .map(path -> new GroupItem(GroupType.IMAGE, null, null, List.of(path)))
                    .collect(Collectors.toList());
        }

        if (currentMode == GroupMode.BY_DATE) {
            return groupByDate(imagePaths, metadataMap);
        }

        if (currentMode == GroupMode.BY_LOCATION) {
            return groupByLocation(imagePaths, metadataMap);
        }

        return imagePaths.stream()
                .map(path -> new GroupItem(GroupType.IMAGE, null, null, List.of(path)))
                .collect(Collectors.toList());
    }

    /**
     * 按日期分组。
     */
    private List<GroupItem> groupByDate(List<Path> imagePaths, Map<Path, ImageDecoder.ImageMetadata> metadataMap) {
        Map<String, List<Path>> dateGroups = new LinkedHashMap<>();

        for (Path path : imagePaths) {
            String dateKey = getDateKey(path, metadataMap);
            dateGroups.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(path);
        }

        List<GroupItem> result = new ArrayList<>();
        for (Map.Entry<String, List<Path>> entry : dateGroups.entrySet()) {
            result.add(new GroupItem(GroupType.DATE_HEADER, entry.getKey(), null, entry.getValue()));
        }

        return result;
    }

    /**
     * 按地理位置分组。
     */
    private List<GroupItem> groupByLocation(List<Path> imagePaths, Map<Path, ImageDecoder.ImageMetadata> metadataMap) {
        List<LocationCluster> clusters = new ArrayList<>();

        for (Path path : imagePaths) {
            ImageDecoder.ImageMetadata metadata = metadataMap.get(path);
            if (metadata != null && metadata.latitude() != 0 && metadata.longitude() != 0) {
                addToCluster(clusters, path, metadata.latitude(), metadata.longitude());
            } else {
                clusters.add(new LocationCluster(List.of(path), 0, 0, "未知地点"));
            }
        }

        List<GroupItem> result = new ArrayList<>();
        for (LocationCluster cluster : clusters) {
            String locationName = cluster.name;
            result.add(new GroupItem(GroupType.LOCATION_HEADER, locationName, null, cluster.paths));
        }

        return result;
    }

    private void addToCluster(List<LocationCluster> clusters, Path path, double lat, double lon) {
        for (LocationCluster cluster : clusters) {
            if (distance(lat, lon, cluster.centerLat, cluster.centerLon) < LOCATION_CLUSTER_THRESHOLD) {
                cluster.paths.add(path);
                return;
            }
        }
        clusters.add(new LocationCluster(new ArrayList<>(List.of(path)), lat, lon, formatLocation(lat, lon)));
    }

    private double distance(double lat1, double lon1, double lat2, double lon2) {
        return Math.sqrt(Math.pow(lat1 - lat2, 2) + Math.pow(lon1 - lon2, 2));
    }

    private String formatLocation(double lat, double lon) {
        return String.format("%.4f, %.4f", lat, lon);
    }

    private String getDateKey(Path path, Map<Path, ImageDecoder.ImageMetadata> metadataMap) {
        ImageDecoder.ImageMetadata metadata = metadataMap.get(path);
        if (metadata != null && metadata.dateTaken() > 0) {
            LocalDate date = Instant.ofEpochMilli(metadata.dateTaken())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            return date.format(DATE_FORMATTER);
        }
        return "未知日期";
    }

    /**
     * 分组类型。
     */
    public enum GroupType {
        IMAGE,
        DATE_HEADER,
        LOCATION_HEADER
    }

    /**
     * 分组项。
     */
    public static class GroupItem {
        private final GroupType type;
        private final String title;
        private final String subtitle;
        private final List<Path> paths;

        public GroupItem(GroupType type, String title, String subtitle, List<Path> paths) {
            this.type = type;
            this.title = title;
            this.subtitle = subtitle;
            this.paths = paths;
        }

        public GroupType getType() {
            return type;
        }

        public String getTitle() {
            return title;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public List<Path> getPaths() {
            return paths;
        }

        public int getImageCount() {
            return paths.size();
        }
    }

    private static class LocationCluster {
        List<Path> paths;
        double centerLat;
        double centerLon;
        String name;

        LocationCluster(List<Path> paths, double centerLat, double centerLon, String name) {
            this.paths = paths;
            this.centerLat = centerLat;
            this.centerLon = centerLon;
            this.name = name;
        }
    }
}
