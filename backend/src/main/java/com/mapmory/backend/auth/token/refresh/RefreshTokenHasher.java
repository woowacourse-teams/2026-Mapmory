package com.mapmory.backend.auth.token.refresh;

/**
 * refresh 토큰 원문을 저장·조회용 해시로 바꾼다.
 *
 * RefreshTokenService는 해시를 어떻게 만드는지 모르고 이 빈을 불러 받기만 한다.
 * 구현을 바꾸면 이미 저장된 해시와 맞지 않아 기존 refresh가 모두 무효가 된다.
 */
@FunctionalInterface
public interface RefreshTokenHasher {

    String hash(String rawToken);
}
