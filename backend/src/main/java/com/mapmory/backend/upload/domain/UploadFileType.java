package com.mapmory.backend.upload.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum UploadFileType {

    JPEG("image/jpeg", "jpg", Set.of("jpg", "jpeg")),
    PNG("image/png", "png", Set.of("png")),
    WEBP("image/webp", "webp", Set.of("webp")),
    HEIC("image/heic", "heic", Set.of("heic"));

    private final String contentType;
    private final String canonicalExtension;
    private final Set<String> extensions;

    UploadFileType(String contentType, String canonicalExtension, Set<String> extensions) {
        this.contentType = contentType;
        this.canonicalExtension = canonicalExtension;
        this.extensions = extensions;
    }

    public static Optional<UploadFileType> findByContentType(String contentType) {
        if (contentType == null) {
            return Optional.empty();
        }

        String normalizedContentType = contentType.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(fileType -> fileType.contentType.equals(normalizedContentType))
                .findFirst();
    }

    public boolean matchesFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }

        String normalizedFileName = fileName.trim().toLowerCase(Locale.ROOT);
        int extensionIndex = normalizedFileName.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == normalizedFileName.length() - 1) {
            return true;
        }

        String extension = normalizedFileName.substring(extensionIndex + 1);
        return extensions.contains(extension);
    }

    public String contentType() {
        return contentType;
    }

    public String canonicalExtension() {
        return canonicalExtension;
    }
}
