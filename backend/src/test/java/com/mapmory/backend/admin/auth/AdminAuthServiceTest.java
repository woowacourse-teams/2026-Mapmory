package com.mapmory.backend.admin.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAuthServiceTest {
    private final AdminAccountRepository repository = mock(AdminAccountRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AdminAuthService service = new AdminAuthService(repository, encoder);

    @Test
    void BCrypt_해시에_일치하는_비밀번호로_인증한다() {
        account();
        assertThat(service.authenticate("admin", "password")).isEqualTo(new AdminIdentity(1L, "admin"));
    }

    @Test
    void 틀린_비밀번호로는_인증할_수_없다() {
        account();
        assertLoginFailure("admin", "wrong");
    }

    @Test
    void 없는_계정도_동일한_인증_실패_오류를_반환한다() {
        when(repository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertLoginFailure("unknown", "password");
    }

    private void account() {
        AdminAccount account = AdminAccount.create("admin", encoder.encode("password"));
        ReflectionTestUtils.setField(account, "id", 1L);
        when(repository.findByUsername("admin")).thenReturn(Optional.of(account));
    }

    private void assertLoginFailure(String username, String password) {
        assertThatThrownBy(() -> service.authenticate(username, password))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AdminAuthErrorCode.ADMIN_LOGIN_FAILED));
    }
}
