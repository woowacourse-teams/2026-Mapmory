package com.mapmory.backend.group.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 그룹 만들기와 이름 바꾸기 요청. 길이(1~30자, 앞뒤 공백 제외)는 TravelGroup이 확인한다.
 */
public record GroupNameRequest(@NotBlank String name) {
}
