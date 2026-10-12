package com.mapmory.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import com.mapmory.backend.member.AuthProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialIdentityPortsTest {

    private static final FakePort KAKAO = new FakePort(AuthProvider.KAKAO, "kakao-id");
    private static final FakePort GOOGLE = new FakePort(AuthProvider.GOOGLE, "google-sub");

    @Test
    void 제공자에_맞는_포트로_토큰을_확인한다() {
        SocialIdentityPorts ports = new SocialIdentityPorts(List.of(KAKAO, GOOGLE));

        SocialIdentity identity = ports.verify(AuthProvider.GOOGLE, "token");

        assertThat(identity.providerId()).isEqualTo("google-sub");
    }

    @Test
    void 포트가_없는_소셜_제공자가_있으면_만들_수_없다() {
        assertThatThrownBy(() -> new SocialIdentityPorts(List.of(KAKAO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void 게스트는_소셜_로그인으로_확인할_수_없다() {
        SocialIdentityPorts ports = new SocialIdentityPorts(List.of(KAKAO, GOOGLE));

        assertThatThrownBy(() -> ports.verify(AuthProvider.GUEST, "token"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 같은_제공자의_포트가_둘이면_만들_수_없다() {
        List<SocialIdentityPort> duplicated = List.of(KAKAO, GOOGLE, new FakePort(AuthProvider.KAKAO, "second"));

        assertThatThrownBy(() -> new SocialIdentityPorts(duplicated))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void provider가_null인_포트는_원인을_밝히며_실패한다() {
        List<SocialIdentityPort> unnamed = List.of(KAKAO, GOOGLE, new FakePort(null, "id"));

        assertThatThrownBy(() -> new SocialIdentityPorts(unnamed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider()가 null");
    }

    private record FakePort(AuthProvider provider, String providerId) implements SocialIdentityPort {

        @Override
        public SocialIdentity verify(String token) {
            return new SocialIdentity(providerId, null);
        }
    }
}
