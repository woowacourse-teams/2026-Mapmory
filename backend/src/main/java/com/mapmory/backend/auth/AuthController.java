package com.mapmory.backend.auth;

import com.mapmory.backend.auth.dto.KakaoLoginRequest;
import com.mapmory.backend.auth.dto.LoginResponse;
import com.mapmory.backend.auth.dto.LogoutRequest;
import com.mapmory.backend.auth.dto.RefreshRequest;
import com.mapmory.backend.auth.dto.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/kakao")
    public ResponseEntity<LoginResponse> loginWithKakao(@Valid @RequestBody KakaoLoginRequest request) {
        LoginResponse response = authService.loginWithKakao(request.kakaoAccessToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
