package com.mapmory.backend.region.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * 앱이 사진 GPS가 지역 안인지 폰에서 판정할 때 쓰는 경계.
 *
 * 각 링은 [경도, 위도] 점의 목록이고 바깥 테두리만 담는다(호수 같은 구멍은 없다).
 * 섬이 여러 개인 나라는 링이 여러 개다.
 */
public record RegionBoundary(String regionCode, String version, List<List<List<Double>>> rings) {

    public RegionBoundary {
        rings = List.copyOf(rings);
    }

    /**
     * 데이터 버전이 같으면 같은 값. 앱이 저장해 둔 경계가 최신인지 HTTP ETag로 확인할 때 쓴다.
     */
    public String versionTag() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(version.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
        }
    }
}
