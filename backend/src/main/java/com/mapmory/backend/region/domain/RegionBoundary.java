package com.mapmory.backend.region.domain;

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
}
