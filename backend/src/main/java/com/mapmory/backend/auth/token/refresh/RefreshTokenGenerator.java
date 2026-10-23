package com.mapmory.backend.auth.token.refresh;

/**
 * refresh 토큰 원문을 만든다.
 *
 * RefreshTokenService는 원문을 어떻게 만드는지 모르고 이 빈을 불러 받기만 한다.
 * 생성 방식을 바꿀 때는 구현 빈만 교체하면 되고, 테스트에서는 람다로 원하는 원문을 넣을 수 있다.
 */
@FunctionalInterface
public interface RefreshTokenGenerator {

    String generate();
}
