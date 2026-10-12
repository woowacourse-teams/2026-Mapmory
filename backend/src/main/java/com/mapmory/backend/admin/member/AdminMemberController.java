package com.mapmory.backend.admin.member;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.AuthProvider;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    @GetMapping
    public ApiResponse<AdminMemberListResponse> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AuthProvider provider,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.from(adminMemberService.getMembers(keyword, provider, page, size));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> getMemberDetail(
            @PathVariable @Min(1) Long memberId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.from(adminMemberService.getMemberDetail(memberId, page, size));
    }
}
