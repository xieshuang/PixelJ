package com.pixelj.internal.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class L1MemoryCacheTest {

    private L1MemoryCache cache;

    @BeforeEach
    void setUp() {
        cache = new L1MemoryCache(10 * 1024 * 1024, 50);
    }

    @AfterEach
    void tearDown() {
        cache.clear();
    }

    @Test
    void testPutAndGet() {
        BufferedImage image = createTestImage(100, 100);
        String key = "test-key";

        cache.put(key, image);
        BufferedImage retrieved = cache.get(key);

        assertNotNull(retrieved);
        assertEquals(image.getWidth(), retrieved.getWidth());
        assertEquals(image.getHeight(), retrieved.getHeight());
    }

    @Test
    void testGetNonExistent() {
        BufferedImage result = cache.get("non-existent-key");
        assertNull(result);
    }

    @Test
    void testContainsKey() {
        BufferedImage image = createTestImage(100, 100);
        String key = "test-key";

        assertFalse(cache.containsKey(key));
        cache.put(key, image);
        assertTrue(cache.containsKey(key));
    }

    @Test
    void testRemove() {
        BufferedImage image = createTestImage(100, 100);
        String key = "test-key";

        cache.put(key, image);
        assertTrue(cache.containsKey(key));

        cache.remove(key);
        assertFalse(cache.containsKey(key));
    }

    @Test
    void testClear() {
        cache.put("key1", createTestImage(100, 100));
        cache.put("key2", createTestImage(100, 100));
        cache.put("key3", createTestImage(100, 100));

        assertEquals(3, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
    }

    @Test
    void testLRUEviction() {
        L1MemoryCache smallCache = new L1MemoryCache(1024 * 1024, 3);

        smallCache.put("key1", createTestImage(100, 100));
        smallCache.put("key2", createTestImage(100, 100));
        smallCache.put("key3", createTestImage(100, 100));

        smallCache.get("key1");

        smallCache.put("key4", createTestImage(100, 100));

        assertTrue(smallCache.containsKey("key1"));
        assertTrue(smallCache.containsKey("key2") || smallCache.containsKey("key3"));
    }

    @Test
    void testMemoryTracking() {
        long initialMemory = cache.getUsedMemoryBytes();
        assertEquals(0, initialMemory);

        BufferedImage image1 = createTestImage(100, 100);
        BufferedImage image2 = createTestImage(100, 100);

        cache.put("key1", image1);
        cache.put("key2", image2);

        assertTrue(cache.getUsedMemoryBytes() > 0);
        assertEquals(2, cache.size());
    }

    private BufferedImage createTestImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }
}
