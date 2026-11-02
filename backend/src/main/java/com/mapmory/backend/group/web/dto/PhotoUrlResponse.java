package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.recordmedia.ExpiringUrl;

/**
 * 사진 주소만 필요한 곳(앨범 카드, 그룹 카드의 대표 사진)에서 쓴다.
 */
public record PhotoUrlResponse(String viewUrl, long viewUrlExpiresIn) {

    static PhotoUrlResponse fromNullable(ExpiringUrl url) {
        return url == null ? null : new PhotoUrlResponse(url.url(), url.expiresIn());
    }
}
