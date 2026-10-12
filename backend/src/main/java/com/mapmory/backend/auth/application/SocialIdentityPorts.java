package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import com.mapmory.backend.member.AuthProvider;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 제공자별 신원 확인 포트 모음.
 *
 * 소셜 제공자(게스트 제외)마다 포트가 정확히 하나 있어야 한다. 포트가 없거나 둘이면 설정 오류이므로
 * 애플리케이션이 뜰 때 실패시킨다. 제공자는 컨트롤러가 정하므로 클라이언트 입력 오류가 아니다.
 */
@Component
public class SocialIdentityPorts {

    private final Map<AuthProvider, SocialIdentityPort> ports = new EnumMap<>(AuthProvider.class);

    public SocialIdentityPorts(List<SocialIdentityPort> socialIdentityPorts) {
        socialIdentityPorts.forEach(this::register);
        Arrays.stream(AuthProvider.values())
                .filter(provider -> provider != AuthProvider.GUEST)
                .filter(provider -> !ports.containsKey(provider))
                .findFirst()
                .ifPresent(provider -> {
                    throw new IllegalStateException("소셜 로그인 포트가 없는 제공자입니다: " + provider);
                });
    }

    private void register(SocialIdentityPort port) {
        AuthProvider provider = port.provider();
        if (provider == null) {
            // 테스트에서 Adapter를 @MockitoBean으로 바꾸면 provider()가 null이 된다. 원인을 바로 알 수 있게 한다.
            throw new IllegalStateException(port.getClass().getName() + "의 provider()가 null입니다.");
        }
        SocialIdentityPort duplicate = ports.putIfAbsent(provider, port);
        if (duplicate != null) {
            throw new IllegalStateException("같은 제공자의 소셜 로그인 포트가 둘 이상입니다: " + provider);
        }
    }

    public SocialIdentity verify(AuthProvider provider, String token) {
        return find(provider).verify(token);
    }

    private SocialIdentityPort find(AuthProvider provider) {
        SocialIdentityPort port = ports.get(provider);
        if (port == null) {
            throw new IllegalStateException("소셜 로그인 제공자가 아닙니다: " + provider);
        }
        return port;
    }
}
