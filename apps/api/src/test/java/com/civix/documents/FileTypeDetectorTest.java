package com.civix.documents;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.civix.documents.service.FileTypeDetector;

class FileTypeDetectorTest {

    @Test
    void detectsByMagicBytesNotByName() {
        assertThat(FileTypeDetector.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII))).contains(FileTypeDetector.PDF);
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0})).contains(FileTypeDetector.PNG);
        assertThat(FileTypeDetector.detect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0})).contains(FileTypeDetector.JPEG);
        assertThat(FileTypeDetector.detect("MZ\u0090 executable".getBytes(StandardCharsets.ISO_8859_1))).isEmpty();
        assertThat(FileTypeDetector.detect(new byte[0])).isEmpty();
    }
}
