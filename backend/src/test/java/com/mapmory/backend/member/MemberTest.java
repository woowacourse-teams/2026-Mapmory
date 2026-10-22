package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MemberTest {

    private static final String GENERATED_NAME_PATTERN = "회원[1-9]\\d{4}";

    @Test
    void 소셜_회원은_제공자_이름과_관계없이_기본_이름_닉네임으로_시작한다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "500001", "소현", UUID.randomUUID());

        assertThat(member.getName()).isEqualTo("소현");
        assertThat(member.getNickname()).matches(GENERATED_NAME_PATTERN);
    }

    @Test
    void 게스트도_기본_이름_닉네임으로_시작한다() {
        Member guest = Member.ofGuest(UUID.randomUUID().toString(), "회원12345", UUID.randomUUID());

        assertThat(guest.getNickname()).matches(GENERATED_NAME_PATTERN);
    }

    @Test
    void 닉네임을_바꿔도_이름은_그대로다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "500002", "소현", UUID.randomUUID());

        member.changeNickname(" 지수 ");

        assertThat(member.getNickname()).isEqualTo("지수");
        assertThat(member.getName()).isEqualTo("소현");
    }

    @Test
    void 닉네임이_규칙에_맞지_않으면_닉네임이_그대로다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "500003", "소현", UUID.randomUUID());
        String nickname = member.getNickname();

        assertThatThrownBy(() -> member.changeNickname(" "))
                .isInstanceOf(BusinessException.class);
        assertThat(member.getNickname()).isEqualTo(nickname);
    }

    @Test
    void 승격하면_이름은_제공자_이름이_되고_닉네임은_그대로다() {
        Member guest = guest();
        String nickname = guest.getNickname();

        guest.promote(AuthProvider.KAKAO, "500004", "소현");

        assertThat(guest.getName()).isEqualTo("소현");
        assertThat(guest.getNickname()).isEqualTo(nickname);
    }

    @Test
    void 닉네임을_바꾼_게스트가_승격해도_닉네임은_그대로다() {
        Member guest = guest();
        guest.changeNickname("지수");

        guest.promote(AuthProvider.KAKAO, "500005", "소현");

        assertThat(guest.getName()).isEqualTo("소현");
        assertThat(guest.getNickname()).isEqualTo("지수");
    }

    @Test
    void 제공자가_이름을_주지_않으면_승격해도_게스트_이름을_쓴다() {
        Member guest = guest();
        String guestName = guest.getName();

        guest.promote(AuthProvider.APPLE, "apple-sub", null);

        assertThat(guest.getProvider()).isEqualTo(AuthProvider.APPLE);
        assertThat(guest.getName()).isEqualTo(guestName);
    }

    private Member guest() {
        return Member.ofGuest(UUID.randomUUID().toString(), MemberName.generated(), UUID.randomUUID());
    }
}
