package com.mapmory.backend.notification.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.notification.application.PushTokenService;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.web.dto.PushTokenDeleteRequest;
import com.mapmory.backend.notification.web.dto.PushTokenRequest;
import com.mapmory.backend.notification.web.dto.PushTokenResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/push-tokens")
public class PushTokenController {

    private final PushTokenService pushTokenService;

    public PushTokenController(PushTokenService pushTokenService) {
        this.pushTokenService = pushTokenService;
    }

    @PutMapping
    public ResponseEntity<ApiResponse<PushTokenResponse>> register(
            @LoginMember Member member,
            @Valid @RequestBody PushTokenRequest request
    ) {
        PushToken registered = pushTokenService.register(
                member.getId(),
                request.token(),
                request.platform()
        );

        PushTokenResponse response = PushTokenResponse.from(registered);
        return ResponseEntity.ok(ApiResponse.from(response));
    }

    @DeleteMapping
    public ResponseEntity<Void> unregister(
            @LoginMember Member member,
            @Valid @RequestBody PushTokenDeleteRequest request
    ) {
        pushTokenService.unregister(member.getId(), request.pushTokenId());
        return ResponseEntity.noContent().build();
    }
}
