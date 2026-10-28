package com.mapmory.backend.member.web.dto;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;

/**
 * 내 회원 정보.
 *
 * nickname : 다른 회원에게 보이는 이름. 소셜 제공자가 준 이름(name)은 내지 않는다.
 * provider : 로그인 방식(KAKAO·GOOGLE·APPLE·GUEST)
 * 회원 식별자(memberId 등)는 노출하지 않는다. ("내 데이터" 통신은 토큰만으로 충분)
 */
public record MemberResponse(
        String nickname,
        AuthProvider provider
) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getNickname(), member.getProvider());
    }
}
