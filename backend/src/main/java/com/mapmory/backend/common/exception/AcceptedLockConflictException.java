package com.mapmory.backend.common.exception;

import org.springframework.dao.DataAccessException;

/**
 * 결정을 거쳐 알고 받아들인 DB 잠금 충돌. 처리기는 503 RETRY_LATER로 응답하고 WARN으로 남긴다. (#753)
 *
 * <p>ADR 0028은 데드락을 대개 잠금 순서를 어긴 버그로 본다. 그래서 감싸지 않은 잠금 충돌은 처리기가 ERROR로 남겨 알람이 간다.
 * 잠금을 더하지 않고 받아들이기로 결정한 경합만, 그 경합이 나는 곳에서 이 예외로 감싸 WARN으로 낮춘다.
 * 감쌀 때는 결정을 남긴 이슈 번호를 넘겨, 로그에서 어느 결정에 따른 충돌인지 찾게 한다.
 */
public class AcceptedLockConflictException extends RuntimeException {

    private final String decision;

    public AcceptedLockConflictException(String decision, DataAccessException lockFailure) {
        super("결정(" + decision + ")에 따라 받아들인 잠금 충돌", lockFailure);
        this.decision = decision;
    }

    public String getDecision() {
        return decision;
    }

    public DataAccessException getLockFailure() {
        return (DataAccessException) getCause();
    }
}
