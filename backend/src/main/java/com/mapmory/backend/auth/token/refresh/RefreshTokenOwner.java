package com.mapmory.backend.auth.token.refresh;

/**
 * refresh 토큰의 주인(회원)과 세션. 토큰을 잠그기 전에 무엇을 먼저 잠글지 정하는 데 쓴다.
 */
record RefreshTokenOwner(Long memberId, Long familyId) {
}
