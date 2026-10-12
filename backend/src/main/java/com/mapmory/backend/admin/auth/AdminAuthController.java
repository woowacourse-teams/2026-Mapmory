package com.mapmory.backend.admin.auth;

import com.mapmory.backend.admin.account.AdminAccount;
import com.mapmory.backend.admin.auth.dto.AdminLoginRequest;
import com.mapmory.backend.admin.auth.dto.AdminLoginResponse;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    public static final String ADMIN_ID_SESSION_KEY = "ADMIN_ID";

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        AdminAccount account = adminAuthService.authenticate(request);

        HttpSession session = httpRequest.getSession(true);
        // 로그인 전에 만들어진 세션 ID를 로그인 후에도 계속 쓰는 위험을 줄이기 위해 새 값으로 변경
        httpRequest.changeSessionId();
        session.setAttribute(ADMIN_ID_SESSION_KEY, account.getId());

        return ApiResponse.from(new AdminLoginResponse(account.getUsername()));
    }

    @GetMapping("/me")
    public ApiResponse<AdminLoginResponse> me(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);

        if (session == null) {
            throw new BusinessException(AdminAuthErrorCode.ADMIN_SESSION_REQUIRED);
        }

        Object value = session.getAttribute(ADMIN_ID_SESSION_KEY);
        if (!(value instanceof Long adminId)) {
            throw new BusinessException(AdminAuthErrorCode.ADMIN_SESSION_REQUIRED);
        }

        AdminAccount account = adminAuthService.findById(adminId);
        return ApiResponse.from(new AdminLoginResponse(account.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);

        if (session != null) {
            // 세션 만료 시키기
            session.invalidate();
        }

        return ResponseEntity.noContent().build();
    }
}
