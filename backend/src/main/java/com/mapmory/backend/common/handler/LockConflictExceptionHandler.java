package com.mapmory.backend.common.handler;

import com.mapmory.backend.common.ProblemDetailFactory;
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
 *     <li>{@link PessimisticLockingFailureException}: 데드락에서 희생된 쪽, 잠금 대기 시간 초과
 *     ({@code CannotAcquireLockException} 등)</li>
 *     <li>{@link OptimisticLockingFailureException}: 읽은 뒤 다른 트랜잭션이 바꾸거나 지운 행을 고치거나 지우려다 0행이 된 경우
 *     ({@code ObjectOptimisticLockingFailureException})</li>
 * </ul>
 *
 * <p>트랜잭션은 이미 롤백되어 데이터는 그대로다. 같은 요청을 다시 보내면 대개 성공하므로 장애가 아니라 다시 시도할 오류로 알린다.
 * 데드락(event=DEADLOCK)과 이미 바뀐 행(event=STALE_WRITE)은 장애 알람(ERROR)에 섞이지 않게 WARN으로 남긴다.
 * 같은 예외로 오는 잠금 대기 시간 초과(MySQL 1205, 50초)는 오래 열린 트랜잭션 같은 이상 신호라 event=LOCK_WAIT_TIMEOUT, ERROR로 남긴다.
 * 얼마나 자주 생기는지 세고 문의가 오면 요청을 찾도록 requestId를 함께 남긴다.
 * 모든 예외를 받는 {@link UnexpectedExceptionHandler}보다 먼저 고르도록 순서를 앞에 둔다.
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
        boolean lockWaitTimeout = LockWaitTimeouts.isLockWaitTimeout(exception);
        log.atLevel(lockWaitTimeout ? Level.ERROR : Level.WARN)
                .addKeyValue("event", eventOf(exception, lockWaitTimeout))
                .addKeyValue("status", HttpStatus.SERVICE_UNAVAILABLE.value())
                .addKeyValue("httpMethod", request.getMethod())
                .addKeyValue("uri", request.getRequestURI())
                .addKeyValue("requestId", MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                .addKeyValue("exception", exception.getClass().getSimpleName())
                .setCause(exception)
                .log("요청이 겹쳐 DB 잠금 충돌로 처리하지 못해 다시 시도하라고 응답했다");

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

    private static String eventOf(DataAccessException exception, boolean lockWaitTimeout) {
        if (lockWaitTimeout) {
            return "LOCK_WAIT_TIMEOUT";
        }
        return exception instanceof PessimisticLockingFailureException ? "DEADLOCK" : "STALE_WRITE";
    }
}
