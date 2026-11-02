package com.mapmory.backend.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.application.model.SocialIdentity;
import com.mapmory.backend.auth.application.model.SocialProvider;
import com.mapmory.backend.auth.application.port.SocialIdentityPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialIdentityPortsTest {

    private static final FakePort KAKAO = new FakePort(SocialProvider.KAKAO, "kakao-id");
    private static final FakePort GOOGLE = new FakePort(SocialProvider.GOOGLE, "google-sub");
    private static final FakePort APPLE = new FakePort(SocialProvider.APPLE, "apple-sub");

    @Test
    void 제공자에_맞는_포트로_토큰을_확인한다() {
        SocialIdentityPorts ports = new SocialIdentityPorts(List.of(KAKAO, GOOGLE, APPLE));

        SocialIdentity identity = ports.verify(SocialProvider.GOOGLE, "token");

        assertThat(identity.providerId()).isEqualTo("google-sub");
    }

    @Test
    void 포트가_없는_소셜_제공자가_있으면_만들_수_없다() {
        assertThatThrownBy(() -> new SocialIdentityPorts(List.of(KAKAO, APPLE)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE");
    }

    @Test
    void 포트가_없는_제공자가_여럿이면_모두_알린다() {
        assertThatThrownBy(() -> new SocialIdentityPorts(List.of(KAKAO)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE")
                .hasMessageContaining("APPLE");
    }

    @Test
    void 같은_제공자의_포트가_둘이면_만들_수_없다() {
        List<SocialIdentityPort> duplicated =
                List.of(KAKAO, GOOGLE, APPLE, new FakePort(SocialProvider.KAKAO, "second"));

        assertThatThrownBy(() -> new SocialIdentityPorts(duplicated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("둘 이상");
    }

    @Test
    void provider가_null인_포트는_원인을_밝히며_실패한다() {
        List<SocialIdentityPort> unnamed = List.of(KAKAO, GOOGLE, APPLE, new FakePort(null, "id"));

        assertThatThrownBy(() -> new SocialIdentityPorts(unnamed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provider()가 null");
    }

    private record FakePort(SocialProvider provider, String providerId) implements SocialIdentityPort {

        @Override
        public SocialIdentity verify(String token) {
            return new SocialIdentity(providerId, null);
        }
    }
}
