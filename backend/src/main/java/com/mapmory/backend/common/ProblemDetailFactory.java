package com.mapmory.backend.common;

import com.mapmory.backend.common.exception.ErrorCode;
import com.mapmory.backend.common.exception.FieldErrorDetail;
import com.mapmory.backend.common.http.RequestPaths;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailFactory {

    public ResponseEntity<ProblemDetail> from(
            HttpStatus status,
            ErrorCode errorCode,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = create(
                status,
                errorCode.title(),
                detail,
                request
        );
        if (errorCode.code() != null && !errorCode.code().isBlank()) {
            problem.setProperty("code", errorCode.code());
        }
        return response(problem);
    }

    public ResponseEntity<ProblemDetail> validation(
            List<FieldErrorDetail> errors,
            HttpServletRequest request
    ) {
        ProblemDetail problem = create(
                HttpStatus.BAD_REQUEST,
                "요청 값이 올바르지 않습니다.",
                errors.size() + "개의 값이 유효하지 않습니다.",
                request
        );
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return response(problem);
    }

    public ResponseEntity<ProblemDetail> notFound(HttpServletRequest request) {
        return response(create(
                HttpStatus.NOT_FOUND,
                "요청한 경로를 찾을 수 없습니다.",
                "요청한 경로를 처리하는 API가 없습니다.",
                request
        ));
    }

    public ResponseEntity<ProblemDetail> internalServerError(HttpServletRequest request) {
        return response(create(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부 오류가 발생했습니다.",
                "요청을 처리하는 중 오류가 발생했습니다.",
                request
        ));
    }

    private static ProblemDetail create(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        // 원본 URI에는 초대 토큰 같은 경로 변수가 들어가므로 경로 패턴을 쓴다.
        problem.setInstance(RequestPaths.instanceOf(request));
        return problem;
    }

    private static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
