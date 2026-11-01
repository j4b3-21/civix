package com.civix.documents.service;

import java.util.Optional;

/**
 * Detects the real file type from magic bytes (the client-declared content type and file name are ignored).
 * Only PDF, PNG and JPEG are accepted.
 */
public final class FileTypeDetector {

    public static final String PDF = "application/pdf";
    public static final String PNG = "image/png";
    public static final String JPEG = "image/jpeg";

    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46, 0x2D};                         // %PDF-
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}; // \x89PNG\r\n\x1a\n
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_MAGIC)) {
            return Optional.of(PDF);
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        return Optional.empty();
    }

    public static String extensionFor(String mimeType) {
        return switch (mimeType) {
            case PDF -> ".pdf";
            case PNG -> ".png";
            case JPEG -> ".jpg";
            default -> "";
        };
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
