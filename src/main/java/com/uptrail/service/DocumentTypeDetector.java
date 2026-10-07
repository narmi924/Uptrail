package com.uptrail.service;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Recognises the accepted document formats from their first bytes. The browser-supplied content type is
 * not trusted; the file name extension must agree with the detected format so that a renamed file is
 * refused rather than served under a misleading name.
 */
public final class DocumentTypeDetector {

    public enum Format {
        PDF("application/pdf", "pdf", Set.of("pdf")),
        PNG("image/png", "png", Set.of("png")),
        JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg"));

        private final String contentType;
        private final String extension;
        private final Set<String> acceptedExtensions;

        Format(String contentType, String extension, Set<String> acceptedExtensions) {
            this.contentType = contentType;
            this.extension = extension;
            this.acceptedExtensions = acceptedExtensions;
        }

        public String contentType() {
            return contentType;
        }

        public String extension() {
            return extension;
        }

        boolean accepts(String fileExtension) {
            return acceptedExtensions.contains(fileExtension);
        }
    }

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private DocumentTypeDetector() {
    }

    public static Optional<Format> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, PDF_MAGIC)) {
            return Optional.of(Format.PDF);
        }
        if (startsWith(content, PNG_MAGIC)) {
            return Optional.of(Format.PNG);
        }
        if (startsWith(content, JPEG_MAGIC)) {
            return Optional.of(Format.JPEG);
        }
        return Optional.empty();
    }

    /** The detected format, only when the file name has a matching extension. */
    public static Optional<Format> detect(String fileName, byte[] content) {
        String extension = extensionOf(fileName);
        return detect(content).filter(format -> format.accepts(extension));
    }

    static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
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
