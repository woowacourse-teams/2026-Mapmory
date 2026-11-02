package com.mapmory.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SearchQueryTest {

    @Test
    void 앞뒤_공백을_뺀다() {
        assertThat(SearchQuery.from("  경복궁 ").value()).isEqualTo("경복궁");
    }

    @Test
    void 공백을_뺀_길이가_100자면_받는다() {
        assertThat(SearchQuery.from(" " + "가".repeat(100) + " ").value()).hasSize(100);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 비어_있는_검색어를_거부한다(String value) {
        assertInvalidSearchQuery(value);
    }

    @Test
    void 공백을_뺀_길이가_100자를_넘으면_거부한다() {
        assertInvalidSearchQuery("가".repeat(101));
    }

    private static void assertInvalidSearchQuery(String value) {
        assertThatThrownBy(() -> SearchQuery.from(value))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode().code())
                        .isEqualTo("INVALID_SEARCH_QUERY"));
    }
}
