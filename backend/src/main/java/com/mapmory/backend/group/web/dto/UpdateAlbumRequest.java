package com.mapmory.backend.group.web.dto;

import jakarta.validation.constraints.Positive;

/**
 * 앨범 이름·지역 바꾸기 요청. 보낸 값만 바꾸고, 둘 다 없으면 AlbumService가 거절한다.
 */
public record UpdateAlbumRequest(String name, @Positive Long regionId) {
}
