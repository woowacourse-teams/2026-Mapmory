package com.mapmory.backend.travelrecord.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class UnlinkedMediaCleanupPropertiesTest {

    /**
     * 유예 기간이 짧으면 저장 요청이 오는 중인 사진을 지울 수 있다.
     */
    @Test
    void 유예_기간은_하루보다_짧을_수_없다() {
        assertThatThrownBy(() -> new UnlinkedMediaCleanupProperties(false, Duration.ofHours(23)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> new UnlinkedMediaCleanupProperties(false, Duration.ofDays(1)))
                .doesNotThrowAnyException();
    }
}
