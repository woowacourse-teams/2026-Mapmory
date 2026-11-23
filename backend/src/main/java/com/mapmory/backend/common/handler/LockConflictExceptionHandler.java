package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.AcceptedLockConflictException;
import com.mapmory.backend.common.exception.CommonErrorCode;
import com.mapmory.backend.common.exception.LockWaitTimeouts;
import com.mapmory.backend.common.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 같은 데이터를 바꾸는 요청이 겹쳐 생긴 DB 잠금 충돌을 500 대신 503 + Retry-After로 응답한다. (#753)
 *
 * <ul>
 *     <li>{@link PessimisticLockingFailureException}: 데드락에서 희생된 쪽(event=DEADLOCK), 잠금 대기 시간 초과
 *     (event=LOCK_WAIT_TIMEOUT, MySQL 1205)</li>
 *     <li>{@link OptimisticLockingFailureException}: 읽은 뒤 다른 트랜잭션이 바꾸거나 지운 행을 고치거나 지우려다 0행이 된 경우
 *     (event=STALE_WRITE)</li>
 * </ul>
 *
 * <p>트랜잭션은 이미 롤백되어 데이터는 그대로다. 같은 요청을 다시 보내면 대개 성공하므로 응답은 모두 다시 시도할 오류(503)다.
 * 하지만 ADR 0028은 데드락을 대개 잠금 순서를 어긴 버그로 본다. 그래서 감싸지 않은 잠금 충돌은 ERROR로 남겨 알람이 가게 하고,
 * 결정을 거쳐 받아들인 경합({@link AcceptedLockConflictException})만 WARN으로 남긴다. 둘 다 requestId를 함께 남긴다.
 *
 * <p>{@link OptimisticLockingFailureException}을 503으로 바꾸는 것은 {@code @Version} 엔티티가 없어서 맞다.
 * #723에서 {@code @Version}을 도입하면 그 충돌은 "다른 기기에서 바뀌었으니 다시 받아 보라"는 409여야 하므로, 그때 이 매핑을 빼거나
 * 버전 충돌을 따로 처리해야 한다(ADR 0028 #753 업데이트).
 *
 * <p>모든 예외를 받는 {@link UnexpectedExceptionHandler}보다 먼저 고르도록 순서를 앞에 둔다.
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RestControllerAdvice
public class LockConflictExceptionHandler {

    static final int RETRY_AFTER_SECONDS = 1;

    private static final Logger log = LoggerFactory.getLogger(LockConflictExceptionHandler.class);

    private final ProblemDetailFactory problemDetailFactory;

    public LockConflictExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ProblemDetail> handle(DataAccessException exception, HttpServletRequest request) {
        log(Level.ERROR, exception, null, request);
        return retryLater(request);
    }

    @ExceptionHandler(AcceptedLockConflictException.class)
    public ResponseEntity<ProblemDetail> handle(AcceptedLockConflictException exception, HttpServletRequest request) {
        log(Level.WARN, exception.getLockFailure(), exception.getDecision(), request);
        return retryLater(request);
    }

    private static void log(Level level, DataAccessException lockFailure, String decision, HttpServletRequest request) {
        log.atLevel(level)
                .addKeyValue("event", eventOf(lockFailure))
                .addKeyValue("accepted", decision != null)
                .addKeyValue("decision", decision)
                .addKeyValue("status", HttpStatus.SERVICE_UNAVAILABLE.value())
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("requestId", MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                .addKeyValue("exception", lockFailure.getClass().getSimpleName())
                .setCause(lockFailure)
                .log("요청이 겹쳐 DB 잠금 충돌로 처리하지 못해 다시 시도하라고 응답했다");
    }

    private ResponseEntity<ProblemDetail> retryLater(HttpServletRequest request) {
        CommonErrorCode errorCode = CommonErrorCode.RETRY_LATER;
        ResponseEntity<ProblemDetail> response = problemDetailFactory.from(
                HttpStatus.SERVICE_UNAVAILABLE, errorCode, errorCode.detail(), request);
        ProblemDetail problem = response.getBody();
        problem.setProperty("retryAfterSeconds", RETRY_AFTER_SECONDS);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .headers(response.getHeaders())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS))
                .body(problem);
    }

    private static String eventOf(DataAccessException lockFailure) {
        if (LockWaitTimeouts.isLockWaitTimeout(lockFailure)) {
            return "LOCK_WAIT_TIMEOUT";
        }
        return lockFailure instanceof PessimisticLockingFailureException ? "DEADLOCK" : "STALE_WRITE";
    }
}
