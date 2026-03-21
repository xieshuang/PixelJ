package com.pixelj.internal.db;

import com.pixelj.spi.ImageDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * H2 内存数据库元数据索引。
 * 用于存储和查询图像文件的元数据信息，包括尺寸、拍摄参数、拍摄日期等。
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
     * 创建 images 表及必要的索引。
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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_path ON images(path)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_directory ON images(directory)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_date_taken ON images(date_taken)");
        }
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
     * 插入或更新单条记录。
     */
    public void insertOrUpdate(ImageRecord record) {
        String sql = """
            MERGE INTO images (path, filename, directory, extension, file_size, last_modified,
                              width, height, camera, lens, focal_length, aperture, shutter_speed,
                              iso, date_taken)
            KEY(path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, record.path());
            pstmt.setString(2, record.filename());
            pstmt.setString(3, record.directory());
            pstmt.setString(4, record.extension());
            pstmt.setLong(5, record.fileSize());
            pstmt.setLong(6, record.lastModified());
            pstmt.setInt(7, record.width());
            pstmt.setInt(8, record.height());
            pstmt.setString(9, record.camera());
            pstmt.setString(10, record.lens());
            pstmt.setString(11, record.focalLength());
            pstmt.setString(12, record.aperture());
            pstmt.setString(13, record.shutterSpeed());
            pstmt.setString(14, record.iso());
            pstmt.setLong(15, record.dateTaken());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert or update record: {}", record.path(), e);
        }
    }

    /**
     * 批量插入或更新记录。
     */
    public void insertOrUpdateBatch(List<ImageRecord> records) {
        String sql = """
            MERGE INTO images (path, filename, directory, extension, file_size, last_modified,
                              width, height, camera, lens, focal_length, aperture, shutter_speed,
                              iso, date_taken)
            KEY(path)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            connection.setAutoCommit(false);
            for (ImageRecord record : records) {
                pstmt.setString(1, record.path());
                pstmt.setString(2, record.filename());
                pstmt.setString(3, record.directory());
                pstmt.setString(4, record.extension());
                pstmt.setLong(5, record.fileSize());
                pstmt.setLong(6, record.lastModified());
                pstmt.setInt(7, record.width());
                pstmt.setInt(8, record.height());
                pstmt.setString(9, record.camera());
                pstmt.setString(10, record.lens());
                pstmt.setString(11, record.focalLength());
                pstmt.setString(12, record.aperture());
                pstmt.setString(13, record.shutterSpeed());
                pstmt.setString(14, record.iso());
                pstmt.setLong(15, record.dateTaken());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            logger.error("Failed to batch insert records", e);
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                // ignore
            }
        }
    }

    /**
     * 按目录查询所有记录。
     */
    public List<ImageRecord> findByDirectory(String directory) {
        List<ImageRecord> results = new ArrayList<>();
        String sql = "SELECT * FROM images WHERE directory = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, directory);
            try (ResultSet rs = pstmt.executeQuery()) {
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
     * 检查缓存是否有效。
     * 通过对比文件的 lastModified 判断。
     */
    public boolean isCacheValid(String directory, Map<Path, Long> fileLastModifiedMap) {
        List<ImageRecord> cachedRecords = findByDirectory(directory);
        if (cachedRecords.isEmpty()) {
            return false;
        }

        Map<String, Long> cachedMap = new HashMap<>();
        for (ImageRecord record : cachedRecords) {
            cachedMap.put(record.path(), record.lastModified());
        }

        for (Map.Entry<Path, Long> entry : fileLastModifiedMap.entrySet()) {
            String pathStr = entry.getKey().toString();
            Long fileModified = entry.getValue();
            Long cachedModified = cachedMap.get(pathStr);

            if (cachedModified == null || !cachedModified.equals(fileModified)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 获取目录的元数据映射。
     */
    public Map<Path, ImageDecoder.ImageMetadata> getMetadataMap(String directory) {
        Map<Path, ImageDecoder.ImageMetadata> result = new HashMap<>();
        List<ImageRecord> records = findByDirectory(directory);

        for (ImageRecord record : records) {
            Path path = Path.of(record.path());
            ImageDecoder.ImageMetadata metadata = new ImageDecoder.ImageMetadata(
                    record.camera(),
                    record.lens(),
                    record.focalLength(),
                    record.aperture(),
                    record.shutterSpeed(),
                    record.iso(),
                    record.fileSize(),
                    record.lastModified(),
                    record.dateTaken(),
                    0,
                    0
            );
            result.put(path, metadata);
        }

        return result;
    }

    /**
     * 将 ResultSet 映射为 ImageRecord。
     */
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
                rs.getLong("date_taken")
        );
    }

    /**
     * 图像记录数据结构。
     * 包含图像文件的所有元数据信息。
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
            long dateTaken
    ) {
    }
}
