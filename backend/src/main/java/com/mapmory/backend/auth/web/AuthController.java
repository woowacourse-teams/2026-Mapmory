package com.mapmory.backend.auth.web;

import com.mapmory.backend.auth.application.AuthService;
import com.mapmory.backend.auth.web.dto.GoogleLoginRequest;
import com.mapmory.backend.auth.web.dto.KakaoLoginRequest;
import com.mapmory.backend.auth.web.dto.LoginResponse;
import com.mapmory.backend.auth.web.dto.LogoutRequest;
import com.mapmory.backend.auth.web.dto.RefreshRequest;
import com.mapmory.backend.auth.web.dto.TokenResponse;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.AuthProvider;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /**
     * 인증이 필요 없는 경로지만, 게스트가 계정을 연결하는 경우에는 게스트 토큰이 함께 온다.
     * 토큰이 없거나 익명이면 principal 타입이 맞지 않아 null로 해석되고, 일반 로그인으로 처리된다.
     */
    @PostMapping("/login/kakao")
    public ApiResponse<LoginResponse> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequest request,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        return ApiResponse.from(LoginResponse.from(
                authService.loginWithSocial(AuthProvider.KAKAO, request.kakaoAccessToken(), authenticatedMemberId)));
    }

    /**
     * 카카오 로그인과 같다. 앱이 구글 SDK로 받은 ID token을 보내며, 게스트 토큰이 있으면 승격한다.
     */
    @PostMapping("/login/google")
    public ApiResponse<LoginResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request,
            @AuthenticationPrincipal Long authenticatedMemberId
    ) {
        return ApiResponse.from(LoginResponse.from(
                authService.loginWithSocial(AuthProvider.GOOGLE, request.idToken(), authenticatedMemberId)));
    }

    @PostMapping("/login/guest")
    public ApiResponse<LoginResponse> loginAsGuest() {
        return ApiResponse.from(LoginResponse.from(authService.loginAsGuest()));
    }

    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.from(TokenResponse.from(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
