package com.mapmory.backend.notification.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import com.mapmory.backend.notification.exception.NotificationErrorCode;
import java.util.Collection;
import java.util.Objects;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기기 토큰 등록과 삭제. 앱은 로그인 뒤와 FCM이 토큰을 새로 줄 때 등록하고, 로그아웃 전에 지운다.
 */
@Service
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final ActiveMemberGuard activeMemberGuard;
    private final TransactionTemplate registrationTransaction;

    public PushTokenService(
            PushTokenRepository pushTokenRepository,
            ActiveMemberGuard activeMemberGuard,
            PlatformTransactionManager transactionManager
    ) {
        this.pushTokenRepository = pushTokenRepository;
        this.activeMemberGuard = activeMemberGuard;
        this.registrationTransaction = new TransactionTemplate(transactionManager);

        // 등록 단위의 커밋·롤백까지 execute() 안에서 끝내고 밖에서 오류를 분류한다.
        // 외부 트랜잭션에서 호출해도 독립적으로 완료되므로 등록 전용 경계로 사용한다.
        this.registrationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 같은 회원의 재등록은 기존 ID를 유지한다.
     * 다른 회원이 같은 토큰을 등록하면 새 등록 ID를 발급한다.
     */
    public PushToken register(Long memberId, String token, PushPlatform platform) {
        try {
            // 템플릿을 만드는 것만으로는 트랜잭션이 시작되지 않는다.
            // execute()로 조회·잠금·삭제·저장과 커밋까지 하나의 범위로 감싼다.
            return Objects.requireNonNull(registrationTransaction.execute(status ->
                    registerInTransaction(memberId, token, platform)));
        } catch (DataIntegrityViolationException exception) {
            // 토큰 UNIQUE 위반만 등록 충돌로 바꾸고 다른 제약 오류는 숨기지 않는다.
            if (!PushTokenConflict.isCausedBy(exception)) {
                throw exception;
            }
            throw registrationConflict(exception);
        } catch (ConcurrencyFailureException exception) {
            // 잠금 획득 실패·교착 등은 트랜잭션이 종료된 뒤 409로 변환한다.
            throw registrationConflict(exception);
        }
    }

    // 같은 객체의 private 메서드에 @Transactional을 붙이는 대신
    // 위의 TransactionTemplate 실행 범위 안에서 호출한다.
    private PushToken registerInTransaction(Long memberId, String token, PushPlatform platform) {
        activeMemberGuard.require(memberId);

        PushToken existing = pushTokenRepository
                .findByTokenForUpdate(token)
                .orElse(null);

        // 처음 등록한 토큰 -> 새 객체 저장
        if (existing == null) {
            return pushTokenRepository.saveAndFlush(
                    new PushToken(memberId, token, platform)
            );
        }

        // 같은 회원이 재등록 -> 플랫폼 갱신 및 기존 ID 유지
        if (existing.getMemberId().equals(memberId)) {
            existing.assignTo(memberId, platform);
            return existing;
        }

        // 계정 전환 시 이전 등록 ID를 새 회원에게 재사용하지 않는다.
        pushTokenRepository.delete(existing);
        // 같은 token의 INSERT 전에 DELETE SQL을 실행한다.
        // 커밋은 아니므로 이후 저장에 실패하면 기존 행 삭제도 함께 롤백된다.
        pushTokenRepository.flush();

        return pushTokenRepository.saveAndFlush(new PushToken(memberId, token, platform));
    }

    /**
     * FCM이 더 이상 유효하지 않다고 답한 토큰을 지운다. 앱을 지운 기기 등이다.
     */
    @Transactional
    public void removeInvalid(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            pushTokenRepository.deleteAllByTokenIn(tokens);
        }
    }

    /**
     * 로그인 회원의 등록 ID만 지운다. 없거나 다른 회원의 등록이면 아무것도 하지 않는다.
     */
    @Transactional
    public void unregister(Long memberId, Long pushTokenId) {
        // token 값이 같더라도 새로 생성된 다른 등록 ID는 삭제하지 않는다.
        pushTokenRepository.deleteByIdAndMemberId(pushTokenId, memberId);
    }

    /**
     * 해당 푸시 등록이 회원에게 속하는지 확인한다.
     * 없거나 다른 회원의 등록이면 false를 반환한다.
     */
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Long pushTokenId, Long memberId) {
        return pushTokenRepository
                .findByIdAndMemberId(pushTokenId, memberId)
                .isPresent();
    }

    private BusinessException registrationConflict(Throwable cause) {
        NotificationErrorCode error = NotificationErrorCode.PUSH_REGISTRATION_CONFLICT;
        return new BusinessException(error, error.detail(), cause);
    }
}
