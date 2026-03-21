package com.pixelj.internal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * H2 内存数据库元数据索引。
 * 用于存储和查询图像文件的元数据信息，包括尺寸、拍摄参数、GPS等。
 */
public class MetadataIndex {

    private static final Logger logger = LoggerFactory.getLogger(MetadataIndex.class);

    private final Connection connection;

    public MetadataIndex() throws SQLException {
        this.connection = DriverManager.getConnection("jdbc:h2:mem:pixelj;DB_CLOSE_DELAY=-1");
        initializeSchema();
        logger.info("MetadataIndex initialized");
    }

    /**
     * 初始化数据库表结构。
     */
    private void initializeSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS images (
                    id INTEGER AUTO_INCREMENT PRIMARY KEY,
                    path VARCHAR(65535) NOT NULL UNIQUE,
                    filename VARCHAR(1024) NOT NULL,
                    directory VARCHAR(65535) NOT NULL,
                    extension VARCHAR(32) NOT NULL,
                    file_size BIGINT,
                    last_modified BIGINT,
                    width INTEGER,
                    height INTEGER,
                    camera VARCHAR(256),
                    lens VARCHAR(256),
                    focal_length VARCHAR(64),
                    aperture VARCHAR(64),
                    shutter_speed VARCHAR(64),
                    iso VARCHAR(64),
                    date_taken BIGINT,
                    latitude DOUBLE,
                    longitude DOUBLE,
                    location_name VARCHAR(128),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_images_path ON images(path)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_images_directory ON images(directory)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_images_date_taken ON images(date_taken)");
        }
    }

    /**
     * 保存或更新图像记录。
     */
    public void saveImageRecord(ImageRecord record) {
        String sql = """
            MERGE INTO images (path, filename, directory, extension, file_size, last_modified,
                              width, height, camera, lens, focal_length, aperture, shutter_speed, iso,
                              date_taken, latitude, longitude, location_name, updated_at)
            KEY(path) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, record.path());
            ps.setString(2, record.filename());
            ps.setString(3, record.directory());
            ps.setString(4, record.extension());
            ps.setLong(5, record.fileSize());
            ps.setLong(6, record.lastModified());
            ps.setInt(7, record.width());
            ps.setInt(8, record.height());
            ps.setString(9, record.camera());
            ps.setString(10, record.lens());
            ps.setString(11, record.focalLength());
            ps.setString(12, record.aperture());
            ps.setString(13, record.shutterSpeed());
            ps.setString(14, record.iso());
            ps.setLong(15, record.dateTaken());
            ps.setDouble(16, record.latitude());
            ps.setDouble(17, record.longitude());
            ps.setString(18, record.locationName());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save image record: {}", record.path(), e);
        }
    }

    /**
     * 批量保存图像记录。
     */
    public void saveImageRecords(List<ImageRecord> records) {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            logger.error("Failed to set auto-commit false", e);
            return;
        }
        try {
            for (ImageRecord record : records) {
                saveImageRecord(record);
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ex) {
                logger.error("Failed to rollback", ex);
            }
            logger.error("Failed to batch save image records", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                logger.error("Failed to reset auto-commit", e);
            }
        }
    }

    /**
     * 按路径查询记录。
     */
    public Optional<ImageRecord> findByPath(String path) {
        String sql = "SELECT * FROM images WHERE path = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, path);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find by path: {}", path, e);
        }
        return Optional.empty();
    }

    /**
     * 按目录查询所有记录。
     */
    public List<ImageRecord> findByDirectory(String directory) {
        List<ImageRecord> results = new ArrayList<>();
        String sql = "SELECT * FROM images WHERE directory = ? ORDER BY date_taken DESC, last_modified DESC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, directory);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToRecord(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find by directory: {}", directory, e);
        }
        return results;
    }

    /**
     * 获取目录下所有图片的元数据映射。
     */
    public Map<String, ImageRecord> getMetadataMapByDirectory(String directory) {
        Map<String, ImageRecord> map = new HashMap<>();
        for (ImageRecord record : findByDirectory(directory)) {
            map.put(record.path(), record);
        }
        return map;
    }

    /**
     * 删除记录。
     */
    public void deleteByPath(String path) {
        String sql = "DELETE FROM images WHERE path = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, path);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to delete by path: {}", path, e);
        }
    }

    /**
     * 检查缓存是否有效（文件修改时间是否一致）。
     */
    public boolean isCacheValid(String directory, Map<String, Long> fileLastModifiedMap) {
        String sql = "SELECT path, last_modified FROM images WHERE directory = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, directory);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> cached = new HashMap<>();
                while (rs.next()) {
                    cached.put(rs.getString("path"), rs.getLong("last_modified"));
                }
                return cached.equals(fileLastModifiedMap);
            }
        } catch (SQLException e) {
            logger.error("Failed to check cache validity", e);
            return false;
        }
    }

    private ImageRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        return new ImageRecord(
                rs.getString("path"),
                rs.getString("filename"),
                rs.getString("directory"),
                rs.getString("extension"),
                rs.getLong("file_size"),
                rs.getLong("last_modified"),
                rs.getInt("width"),
                rs.getInt("height"),
                rs.getString("camera"),
                rs.getString("lens"),
                rs.getString("focal_length"),
                rs.getString("aperture"),
                rs.getString("shutter_speed"),
                rs.getString("iso"),
                rs.getLong("date_taken"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("location_name")
        );
    }

    /**
     * 关闭数据库连接。
     */
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            logger.error("Failed to close connection", e);
        }
    }

    /**
     * 图像记录数据结构。
     */
    public record ImageRecord(
            String path,
            String filename,
            String directory,
            String extension,
            long fileSize,
            long lastModified,
            int width,
            int height,
            String camera,
            String lens,
            String focalLength,
            String aperture,
            String shutterSpeed,
            String iso,
            long dateTaken,
            double latitude,
            double longitude,
            String locationName
    ) {
    }
}
