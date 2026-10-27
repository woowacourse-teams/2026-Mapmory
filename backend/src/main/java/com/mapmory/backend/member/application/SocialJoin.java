package com.mapmory.backend.member.application;

import com.mapmory.backend.member.Member;

/**
 * 소셜 로그인의 회원 판단 결과.
 *
 * member         : 로그인할 회원
 * type           : 가입·승격·기존 계정 중 어떤 경우인가
 * guestAbandoned : 기존 계정으로 들어오면서 게스트를 두고 떠났는가. EXISTING_ACCOUNT이고 게스트로 사용 중이었을 때만 true다.
 */
public record SocialJoin(
        Member member,
        SocialJoinType type,
        boolean guestAbandoned
) {
}
