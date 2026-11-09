package com.mapmory.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.exception.PlaceErrorCode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class CoordinateTest {

    @Test
    void 위도_경도를_소수점_아래_6자리로_보낸다() {
        assertThat(new Coordinate(35.8714, 128.6014).latitudeText()).isEqualTo("35.871400");
        assertThat(new Coordinate(35.8714, 128.6014).longitudeText()).isEqualTo("128.601400");
        assertThat(new Coordinate(-90, 180).latitudeText()).isEqualTo("-90.000000");
        assertThat(new Coordinate(-90, 180).longitudeText()).isEqualTo("180.000000");
    }

    @ParameterizedTest
    @CsvSource({"90.0001,0", "-90.0001,0", "0,180.0001", "0,-180.0001", "NaN,0", "0,Infinity"})
    void 범위를_벗어난_좌표는_거절한다(double latitude, double longitude) {
        assertThatThrownBy(() -> new Coordinate(latitude, longitude))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(PlaceErrorCode.INVALID_COORDINATE));
    }

    @Test
    void 문자열로_바꿔도_위치를_드러내지_않는다() {
        assertThat(new Coordinate(35.8714, 128.6014).toString()).doesNotContain("35.87").doesNotContain("128.60");
    }
}
