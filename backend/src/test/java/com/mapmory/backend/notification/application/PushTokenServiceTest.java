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

    @Test
    void 회전한_토큰은_기존_등록을_갱신하고_설정을_저장한다() {
        PushToken previous = new PushToken(1L, "old", PushPlatform.ANDROID);
        org.springframework.test.util.ReflectionTestUtils.setField(previous, "id", 7L);
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(previous));
        given(repository.saveAndFlush(previous)).willReturn(previous);
        assertThat(service.register(1L, "new", PushPlatform.ANDROID, 7L, false, true)).isSameAs(previous);
        assertThat(previous.getId()).isEqualTo(7L);
        assertThat(previous.getToken()).isEqualTo("new");
        assertThat(previous.allowsCompletion("PHOTO_RECORD_COMPLETED")).isFalse();
        assertThat(previous.allowsCompletion("VIDEO_COMPLETED")).isTrue();
    }

    @Test
    void 다른_회원의_이전_등록은_변경하지_않는다() {
        PushToken foreign = new PushToken(2L, "old", PushPlatform.ANDROID);
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.register(1L, "new", PushPlatform.ANDROID, 7L, false, false))
                .isInstanceOf(BusinessException.class);
        assertThat(foreign.getToken()).isEqualTo("old");
        verify(repository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void 정리된_이전_등록은_새_등록으로_복구한다() {
        given(repository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        PushToken result = service.register(1L, "new", PushPlatform.ANDROID, 7L, true, false);
        assertThat(result.getToken()).isEqualTo("new");
        assertThat(result.allowsCompletion("VIDEO_COMPLETED")).isFalse();
    }

    @Test
    void 기존_클라이언트는_두_완료_알림을_허용한다() {
        given(repository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));
        PushToken result = service.register(1L, "new", PushPlatform.ANDROID);
        assertThat(result.allowsCompletion("PHOTO_RECORD_COMPLETED")).isTrue();
        assertThat(result.allowsCompletion("VIDEO_COMPLETED")).isTrue();
    }

    @Test
    void 회전_대상_토큰이_다른_등록에_있으면_기존_등록을_보존한다() {
        PushToken previous = new PushToken(1L, "old", PushPlatform.ANDROID);
        PushToken collision = new PushToken(2L, "new", PushPlatform.ANDROID);
        org.springframework.test.util.ReflectionTestUtils.setField(previous, "id", 7L);
        org.springframework.test.util.ReflectionTestUtils.setField(collision, "id", 8L);
        given(repository.findByTokenForUpdate("new")).willReturn(Optional.of(collision));
        given(repository.findByIdForUpdate(7L)).willReturn(Optional.of(previous));
        assertThatThrownBy(() -> service.register(1L, "new", PushPlatform.ANDROID, 7L, true, true))
                .isInstanceOf(BusinessException.class);
        assertThat(previous.getToken()).isEqualTo("old");
        assertThat(collision.getMemberId()).isEqualTo(2L);
        verify(repository, org.mockito.Mockito.never()).delete(any());
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
