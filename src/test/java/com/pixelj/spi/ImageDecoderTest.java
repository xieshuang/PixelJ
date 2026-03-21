package com.pixelj.spi;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ImageDecoderTest {

    @Test
    void testDecodeOptionsDefaults() {
        ImageDecoder.DecodeOptions options = ImageDecoder.DecodeOptions.DEFAULT;
        assertEquals(0, options.targetWidth());
        assertEquals(0, options.targetHeight());
        assertTrue(options.preserveAspectRatio());
    }

    @Test
    void testDecodeOptionsThumbnail() {
        ImageDecoder.DecodeOptions options = ImageDecoder.DecodeOptions.thumbnail(400, 300);
        assertEquals(400, options.targetWidth());
        assertEquals(300, options.targetHeight());
        assertTrue(options.preserveAspectRatio());
    }

    @Test
    void testDecodeOptionsScaled() {
        ImageDecoder.DecodeOptions options = ImageDecoder.DecodeOptions.scaled(800, 600);
        assertEquals(800, options.targetWidth());
        assertEquals(600, options.targetHeight());
        assertTrue(options.preserveAspectRatio());
    }

    @Test
    void testImageMetadata() {
        long now = System.currentTimeMillis();
        ImageDecoder.ImageMetadata metadata = new ImageDecoder.ImageMetadata(
                "Canon EOS 5D",
                "EF 24-70mm f/2.8L",
                "50mm",
                "f/2.8",
                "1/250",
                "ISO 400",
                1024L * 1024 * 25,
                now,
                now,
                31.2304,
                121.4737
        );

        assertEquals("Canon EOS 5D", metadata.camera());
        assertEquals("EF 24-70mm f/2.8L", metadata.lens());
        assertEquals("50mm", metadata.focalLength());
        assertEquals("f/2.8", metadata.aperture());
        assertEquals("1/250", metadata.shutterSpeed());
        assertEquals("ISO 400", metadata.iso());
        assertEquals(1024L * 1024 * 25, metadata.fileSize());
        assertEquals(now, metadata.dateTaken());
        assertEquals(31.2304, metadata.latitude());
        assertEquals(121.4737, metadata.longitude());
    }

    @Test
    void testDefaultPriority() {
        TestDecoder decoder = new TestDecoder();
        assertEquals(100, decoder.getPriority());
    }

    private static class TestDecoder implements ImageDecoder {
        @Override
        public String[] supportedMimeTypes() {
            return new String[]{"image/test"};
        }

        @Override
        public String[] supportedExtensions() {
            return new String[]{"test"};
        }

        @Override
        public Optional<java.awt.image.BufferedImage> decode(java.nio.file.Path path, DecodeOptions options) {
            return Optional.empty();
        }
    }
}
