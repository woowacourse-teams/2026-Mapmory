package com.mapmory.backend.sharevideo;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record ShareVideoDownloadUrl(
        String url,
        long expiresIn
) {
    public ShareVideoDownloadUrl {
        Objects.requireNonNull(url, "url must not be null");

        if (url.isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }

        if (expiresIn <= 0) {
            throw new IllegalArgumentException("expiresIn must be positive");
        }
    }

    public static ShareVideoDownloadUrl from(
            final URI url,
            final Duration expiration
    ) {
        return new ShareVideoDownloadUrl(url.toString(), expiration.toSeconds());
    }
}
