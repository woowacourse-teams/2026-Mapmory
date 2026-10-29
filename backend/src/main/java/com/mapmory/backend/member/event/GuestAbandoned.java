package com.mapmory.backend.member.event;

import java.time.LocalDateTime;

/**
 * 게스트 회원이 버려졌다. (E06, ADR 0021)
 *
 * 게스트로 사용하던 사람이 이미 가입한 소셜 계정으로 로그인해 기존 회원으로 들어갔고,
 * 게스트 회원에는 아무도 접근할 수 없게 됐다. 게스트 행과 기록은 남아 있다.
 * 엔티티를 담지 않는다. 받는 쪽은 guestMemberId로 자기 데이터를 찾는다.
 *
 * 받는 쪽
 *   - auth: 게스트의 refresh 토큰을 모두 폐기한다. 로그인과 같은 트랜잭션에서 동기로 받는다. (MemberRefreshTokenRevoker)
 *   - notification: 게스트의 기기 토큰을 모두 지운다. 로그인이 커밋된 뒤에 받는다. (InactiveMemberListener)
 *   - 여행기록·미디어 등 다른 컨텍스트는 파기 기간을 정해 게스트의 데이터를 지운다.
 *     로그인이 커밋된 뒤에 처리하도록 @TransactionalEventListener(phase = AFTER_COMMIT)로 받는다. (아직 없음)
 *
 * guestMemberId : 버려진 게스트 회원
 * abandonedAt   : 버려진 시각
 */
public record GuestAbandoned(
        Long guestMemberId,
        LocalDateTime abandonedAt
) {
}
