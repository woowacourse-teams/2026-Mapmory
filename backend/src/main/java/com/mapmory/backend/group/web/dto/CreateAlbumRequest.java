package com.mapmory.backend.group.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 새 앨범 열기 요청. 이름 길이(1~200자, 앞뒤 공백 제외)는 Album이 확인한다.
 *
 * @param name 보내지 않거나 비우면 기록 제목, 제목도 비었으면 지역 이름이다({@code Album.nameFor})
 * @param regionId 보내지 않으면 그 기록의 지역이다
 */
public record CreateAlbumRequest(
        @NotNull @Positive Long travelRecordId,
        String name,
        @Positive Long regionId
) {
}
