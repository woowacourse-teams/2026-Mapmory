package com.mapmory.backend.group.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class InvitationLinkPropertiesTest {

    @Test
    void 주소_앞부분_뒤에_토큰을_붙여_링크를_만든다() {
        InvitationLinkProperties properties = new InvitationLinkProperties("https://map-mory.com/invite/");

        assertThat(properties.linkOf("abc_DEF-123")).isEqualTo("https://map-mory.com/invite/abc_DEF-123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"map-mory.com/invite/", "https://map-mory.com/invite?x=/", "https://map-mory.com/a#b/", "https://map-mory.com/invite", "ftp://map-mory.com/invite/",
            "https:///invite/"})
    void 주소가_http로_시작하지_않거나_슬래시로_끝나지_않으면_뜨지_않는다(String baseUrl) {
        assertThatThrownBy(() -> new InvitationLinkProperties(baseUrl))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void 주소가_비어_있으면_기본_주소를_쓴다(String baseUrl) {
        assertThat(new InvitationLinkProperties(baseUrl).baseUrl()).isEqualTo("https://map-mory.com/invite/");
    }
}
