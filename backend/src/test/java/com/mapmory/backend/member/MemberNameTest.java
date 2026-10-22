package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.exception.MemberErrorCode;
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

    @Test
    void 가입하면_이름은_그대로_두고_닉네임은_기본_이름으로_시작한다() {
        MemberName memberName = MemberName.startingWith("소현");

        assertThat(memberName.name()).isEqualTo("소현");
        assertThat(memberName.nickname()).matches(GENERATED_NAME_PATTERN);
    }

    @Test
    void 승격하면_이름만_제공자_이름으로_바뀐다() {
        MemberName guest = new MemberName("회원11111", "지수");

        MemberName promoted = guest.promote("소현");

        assertThat(promoted).isEqualTo(new MemberName("소현", "지수"));
    }

    @Test
    void 승격할_때_긴_제공자_이름은_50자로_자른다() {
        MemberName promoted = new MemberName("회원11111", "지수").promote("가".repeat(51));

        assertThat(promoted.name()).isEqualTo("가".repeat(50));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void 제공자가_이름을_주지_않으면_승격해도_그대로다(String providerName) {
        MemberName guest = new MemberName("회원11111", "지수");

        assertThat(guest.promote(providerName)).isEqualTo(guest);
    }

    @Test
    void 닉네임을_바꿔도_이름은_그대로다() {
        MemberName changed = new MemberName("소현", "회원11111").changeNickname("지수");

        assertThat(changed).isEqualTo(new MemberName("소현", "지수"));
    }

    @Test
    void 이름이나_닉네임이_없으면_만들_수_없다() {
        assertThatNullPointerException().isThrownBy(() -> new MemberName(null, "지수"));
        assertThatNullPointerException().isThrownBy(() -> new MemberName("소현", null));
    }

    @Test
    void DB에_있던_값은_닉네임_규칙과_관계없이_읽는다() {
        MemberName legacy = new MemberName("가".repeat(50), "가".repeat(20) + "\n");

        assertThat(legacy.nickname()).endsWith("\n");
    }

    @Test
    void 닉네임은_앞뒤_공백을_지워서_쓴다() {
        assertThat(changedNickname("  지수  ")).isEqualTo("지수");
    }

    @Test
    void 닉네임은_1자부터_20자까지_쓸_수_있다() {
        assertThat(changedNickname("지")).isEqualTo("지");
        assertThat(changedNickname("가".repeat(20))).isEqualTo("가".repeat(20));
    }

    @Test
    void 닉네임_길이는_코드_포인트로_센다() {
        String emoji = "😀".repeat(20);

        assertThat(changedNickname(emoji)).isEqualTo(emoji);
    }

    @Test
    void 닉네임_안의_공백은_그대로_둔다() {
        assertThat(changedNickname("여행 좋아")).isEqualTo("여행 좋아");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "　"})
    void 닉네임이_비어_있거나_공백뿐이면_거절한다(String nickname) {
        assertInvalidNickname(nickname);
    }

    @Test
    void 닉네임이_20자를_넘으면_자르지_않고_거절한다() {
        assertInvalidNickname("가".repeat(21));
    }

    @ParameterizedTest
    @ValueSource(strings = {"지\n수", "지\u0000수", "지\u007F수"})
    void 닉네임에_제어_문자가_있으면_거절한다(String nickname) {
        assertInvalidNickname(nickname);
    }

    private void assertInvalidNickname(String nickname) {
        assertThatThrownBy(() -> changedNickname(nickname))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_NICKNAME);
    }

    private String changedNickname(String nickname) {
        return MemberName.startingWith("소현").changeNickname(nickname).nickname();
    }
}
