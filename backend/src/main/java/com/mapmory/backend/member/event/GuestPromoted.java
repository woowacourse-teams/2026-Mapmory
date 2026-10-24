package com.mapmory.backend.member.event;

import java.time.LocalDateTime;

/**
 * 게스트 회원이 소셜 회원으로 승격했다. (ADR 0015)
 *
 * 같은 회원 행이 소셜 계정에 연결됐다. 게스트일 때 발급된 세션은 소셜 로그인으로 대체됐으므로 끝낸다.
 * 엔티티를 담지 않는다. 받는 쪽은 memberId로 자기 데이터를 찾는다.
 *
 * 받는 쪽
 *   - auth: 게스트일 때 발급된 refresh 토큰을 모두 폐기한다. 로그인과 같은 트랜잭션에서 동기로 받고,
 *     승격 응답의 토큰은 그 뒤에 발급되므로 살아 있다. (MemberRefreshTokenRevoker)
 *
 * memberId   : 승격한 회원
 * promotedAt : 승격한 시각
 */
public record GuestPromoted(
        Long memberId,
        LocalDateTime promotedAt
) {
}
