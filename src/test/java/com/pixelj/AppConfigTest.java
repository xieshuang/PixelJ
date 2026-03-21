package com.pixelj;

import com.pixelj.internal.cache.L1MemoryCache;
import com.pixelj.util.AppConfig;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void testSingleton() {
        AppConfig config1 = AppConfig.getInstance();
        AppConfig config2 = AppConfig.getInstance();
        assertSame(config1, config2);
    }

    @Test
    void testDefaultValues() {
        AppConfig config = AppConfig.getInstance();
        assertEquals(512, config.getL1CacheMaxMemoryMB());
        assertEquals(1024, config.getL2CacheMaxDiskMB());
        assertEquals(400, config.getThumbnailWidth());
        assertEquals(300, config.getThumbnailHeight());
        assertEquals(3, config.getPrefetchRows());
        assertTrue(config.isHardwareAccelerationEnabled());
    }

    @Test
    void testL1CacheMaxMemoryBytes() {
        AppConfig config = AppConfig.getInstance();
        assertEquals(512L * 1024 * 1024, config.getL1CacheMaxMemoryBytes());
    }

    @Test
    void testL2CacheMaxDiskBytes() {
        AppConfig config = AppConfig.getInstance();
        assertEquals(1024L * 1024 * 1024, config.getL2CacheMaxDiskBytes());
    }
}
