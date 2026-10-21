package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MemberNameTest {

    private static final String GENERATED_NAME_PATTERN = "회원[1-9]\\d{4}";

    @Test
    void 기본_이름은_회원과_다섯_자리_숫자다() {
        assertThat(MemberName.generated()).matches(GENERATED_NAME_PATTERN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void 제공자_이름이_비어_있으면_기본_이름을_쓴다(String providerName) {
        assertThat(MemberName.fromProvider(providerName)).matches(GENERATED_NAME_PATTERN);
    }

    @Test
    void 제공자_이름이_50자_이하면_그대로_쓴다() {
        String name = "가".repeat(50);

        assertThat(MemberName.fromProvider(name)).isEqualTo(name);
    }

    @Test
    void 제공자_이름이_50자를_넘으면_50자로_자른다() {
        assertThat(MemberName.fromProvider("가".repeat(51))).isEqualTo("가".repeat(50));
    }

    @Test
    void 이모지는_코드_포인트_단위로_잘려_깨지지_않는다() {
        String emoji = "😀";

        String name = MemberName.fromProvider(emoji.repeat(51));

        assertThat(name).isEqualTo(emoji.repeat(50));
        assertThat(name.codePointCount(0, name.length())).isEqualTo(50);
    }
}
