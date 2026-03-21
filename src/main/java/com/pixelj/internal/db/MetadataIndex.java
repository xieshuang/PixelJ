package com.pixelj.internal.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * H2 内存数据库元数据索引。
 * 用于存储和查询图像文件的元数据信息，包括尺寸、拍摄参数等。
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
     *
     * @throws SQLException 数据库操作异常
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
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_path ON images(path)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_directory ON images(directory)
            """);

            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_last_modified ON images(last_modified)
            """);
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
     * 图像记录数据结构。
     * 包含图像文件的所有元数据信息。
     *
     * @param path          完整文件路径
     * @param filename      文件名
     * @param directory     所属目录
     * @param extension     文件扩展名
     * @param fileSize      文件大小（字节）
     * @param lastModified 最后修改时间戳
     * @param width         图像宽度
     * @param height        图像高度
     * @param camera        相机型号
     * @param lens          镜头型号
     * @param focalLength   焦距
     * @param aperture      光圈值
     * @param shutterSpeed  快门速度
     * @param iso           ISO感光度
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
            String iso
    ) {
    }
}
