package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.travelrecord.application.TravelRecordCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 여행 일지 생성·수정 v2 요청. v1({@link TravelRecordRequest})과 사진만 다르다.
 *
 * <p>v1은 사진을 Object Key 문자열 목록으로 받는다. v2는 사진 하나의 정보(키, 촬영일)를 한 객체로 묶어 받는다.
 * 배열 순서가 사진 순서다. 사진 장수, 키 형식, 중복 규칙은 v1과 같다. (ADR 0030)
 */
public record TravelRecordV2Request(
        @Pattern(regexp = "[A-Z]{2}", message = "국가 코드는 대문자 2자리여야 합니다.")
        String countryCode,
        @Pattern(regexp = "\\S{1,20}", message = "시도 코드는 공백일 수 없습니다.")
        String provinceCode,
        @Pattern(regexp = "\\S{1,20}", message = "시군구 코드는 공백일 수 없습니다.")
        String districtCode,
        @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
        String title,
        String content,
        @NotNull
        LocalDate startDate,
        LocalDate endDate,
        List<@NotNull PhotoRequest> photos,
        List<Long> tagIds,
        @Size(max = 255, message = "장소 ID는 255자 이하여야 합니다.")
        String placeId
) {

    /**
     * @param objectKey    업로드를 마친 사진의 Object Key
     * @param capturedDate 촬영일(yyyy-MM-dd). 모르면 null이다. 형식이나 범위가 틀려도 저장을 실패시키지 않고
     *                     모르는 날짜로 본다. 카메라 시계가 틀린 사진이 흔해서 문자열로 받는다
     */
    public record PhotoRequest(String objectKey, String capturedDate) {
    }

    public TravelRecordCommand toCommand() {
        List<PhotoRequest> requestedPhotos = photos == null ? List.of() : photos;
        List<String> objectKeys = requestedPhotos.stream()
                .map(PhotoRequest::objectKey)
                .toList();
        Map<String, String> capturedDates = new HashMap<>();
        for (PhotoRequest photo : requestedPhotos) {
            if (photo.objectKey() != null && photo.capturedDate() != null) {
                capturedDates.putIfAbsent(photo.objectKey(), photo.capturedDate());
            }
        }
        return new TravelRecordCommand(
                countryCode,
                provinceCode,
                districtCode,
                title,
                content,
                startDate,
                endDate,
                objectKeys,
                tagIds,
                placeId,
                capturedDates
        );
    }
}
