package com.mapmory.backend.auth.application.port;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.member.AuthProvider;

/**
 * 앱이 소셜 SDK로 받은 토큰을 검증해 신원을 돌려주는 포트.
 *
 * 제공자마다 구현이 하나씩 있고, SocialIdentityPorts가 provider()로 구현을 고른다.
 * 같은 제공자의 구현이 둘이면 애플리케이션이 뜰 때 실패한다.
 * 토큰이 잘못되면 401, 제공자 장애면 503에 해당하는 BusinessException을 던진다.
 */
public interface SocialIdentityPort {

    AuthProvider provider();

    SocialIdentity verify(String token);
}
