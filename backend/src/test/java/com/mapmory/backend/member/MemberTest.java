package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;

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
}
