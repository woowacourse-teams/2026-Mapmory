package com.mapmory.backend.auth.token.refresh;

/**
 * 회전 결과. 새 refresh 원문과 그 소유 회원.
 *
 * 호출부는 트랜잭션 밖에서 access를 발급하므로, 지연 로딩 프록시 대신 회원 id만 넘긴다.
 */
public record RotatedRefreshToken(Long memberId, String refreshToken) {
}
