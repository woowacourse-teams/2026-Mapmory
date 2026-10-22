package com.mapmory.backend.member.web;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.application.MemberService;
import com.mapmory.backend.member.web.dto.MemberResponse;
import com.mapmory.backend.member.web.dto.NicknameChangeRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인한 회원 자신의 정보.
 *
 * member는 auth를 import하지 않으므로 @LoginMember 대신 JWT 필터가 등록한 회원 ID를 직접 받는다.
 * 인증이 필요한 경로라 회원 ID는 항상 있다.
 */
@RestController
@RequestMapping("/api/v1/members/me")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<MemberResponse> findMe(@AuthenticationPrincipal Long memberId) {
        return ApiResponse.from(MemberResponse.from(memberService.findMe(memberId)));
    }

    @PatchMapping("/nickname")
    public ApiResponse<MemberResponse> changeNickname(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody NicknameChangeRequest request
    ) {
        return ApiResponse.from(MemberResponse.from(memberService.changeNickname(memberId, request.nickname())));
    }
}
