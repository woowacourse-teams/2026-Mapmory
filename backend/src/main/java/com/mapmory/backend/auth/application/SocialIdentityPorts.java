package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 제공자별 신원 확인 포트 모음.
 *
 * 소셜 제공자마다 포트가 정확히 하나 있어야 한다. 포트가 없거나 둘이면 설정 오류이므로
 * 애플리케이션이 뜰 때 실패시킨다. 제공자는 컨트롤러가 정하므로 클라이언트 입력 오류가 아니다.
 */
@Component
public class SocialIdentityPorts {

    private final Map<SocialProvider, SocialIdentityPort> ports = new EnumMap<>(SocialProvider.class);

    public SocialIdentityPorts(List<SocialIdentityPort> socialIdentityPorts) {
        socialIdentityPorts.forEach(this::register);
        List<SocialProvider> missing = Arrays.stream(SocialProvider.values())
                .filter(provider -> !ports.containsKey(provider))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("소셜 로그인 포트가 없는 제공자입니다: " + missing);
        }
    }

    private void register(SocialIdentityPort port) {
        SocialProvider provider = port.provider();
        if (provider == null) {
            // 어댑터를 @MockitoBean으로 대체하면 컨텍스트 기동 전에 stub할 수 없어 provider()가 null이 된다.
            // 테스트에서는 SocialIdentityPorts를 목으로 바꾸거나 어댑터 아래 외부 클라이언트를 목으로 바꿀 것.
            throw new IllegalStateException(port.getClass().getName() + "의 provider()가 null입니다.");
        }
        SocialIdentityPort duplicate = ports.putIfAbsent(provider, port);
        if (duplicate != null) {
            throw new IllegalStateException("같은 제공자의 소셜 로그인 포트가 둘 이상입니다: " + provider);
        }
    }

    // 생성자가 모든 소셜 제공자의 포트를 보장하므로 찾지 못하는 경우가 없다.
    public SocialIdentity verify(SocialProvider provider, String token) {
        return ports.get(provider).verify(token);
    }
}
