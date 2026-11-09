package com.mapmory.backend.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import com.mapmory.backend.notification.exception.NotificationErrorCode;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private PushTokenRepository repository;

    @Mock
    private ActiveMemberGuard activeMemberGuard;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PushTokenService service;

    @BeforeEach
    void setUp() {
        given(transactionManager.getTransaction(any())).willReturn(new SimpleTransactionStatus());
        service = new PushTokenService(repository, activeMemberGuard, transactionManager);
    }

    @Test
    void 토큰_중복_오류는_롤백_후_등록_충돌로_변환한다() {
        given(repository.findByTokenForUpdate("token")).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willThrow(violationOf("uk_push_token_token"));

        assertRegistrationConflict();

        verify(transactionManager).rollback(any());
    }

    @Test
    void 다른_제약_위반은_변환하지_않는다() {
        DataIntegrityViolationException failure = violationOf("fk_push_token_member");
        given(repository.findByTokenForUpdate("token")).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willThrow(failure);

        assertThatThrownBy(() -> service.register(1L, "token", PushPlatform.ANDROID)).isSameAs(failure);
        verify(transactionManager).rollback(any());
    }

    @Test
    void 잠금_오류는_롤백_후_등록_충돌로_변환한다() {
        given(repository.findByTokenForUpdate("token"))
                .willThrow(new PessimisticLockingFailureException("lock conflict"));

        assertRegistrationConflict();
        verify(transactionManager).rollback(any());
    }

    @Test
    void 메서드_실행_이후_커밋에서_발생한_토큰_충돌도_변환한다() {
        given(repository.findByTokenForUpdate("token")).willReturn(Optional.empty());
        given(repository.saveAndFlush(any())).willReturn(new PushToken(1L, "token", PushPlatform.ANDROID));
        willThrow(violationOf("uk_push_token_token")).given(transactionManager).commit(any());

        assertRegistrationConflict();
    }

    private void assertRegistrationConflict() {
        assertThatThrownBy(() -> service.register(1L, "token", PushPlatform.ANDROID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(NotificationErrorCode.PUSH_REGISTRATION_CONFLICT));
    }

    private DataIntegrityViolationException violationOf(String constraintName) {
        return new DataIntegrityViolationException("constraint violation",
                new ConstraintViolationException("constraint violation", new SQLException(), constraintName));
    }
}
