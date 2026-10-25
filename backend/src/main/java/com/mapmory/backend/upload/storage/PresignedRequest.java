package com.mapmory.backend.upload.storage;

import java.net.URI;

/**
 * 서명된 요청. 어떤 HTTP 메서드로 서명했는지는 서명한 쪽만 알므로 URL과 함께 돌려준다.
 *
 * url    : 서명된 URL
 * method : 앱이 이 URL을 부를 때 써야 하는 HTTP 메서드
 */
public record PresignedRequest(
        URI url,
        String method
) {
}
