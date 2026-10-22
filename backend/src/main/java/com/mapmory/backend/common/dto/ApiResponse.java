package com.mapmory.backend.common.dto;

/**
 * 성공 응답을 data 필드로 감싼다.
 * 오류 응답은 이 타입을 사용하지 않고 Problem Details 형식을 유지한다.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> from(T data) {
        return new ApiResponse<>(data);
    }
}
