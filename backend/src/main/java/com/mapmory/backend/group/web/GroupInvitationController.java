package com.mapmory.backend.group.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.group.application.GroupInvitationService;
import com.mapmory.backend.group.web.dto.GroupResponse;
import com.mapmory.backend.group.web.dto.InvitationPreviewResponse;
import com.mapmory.backend.group.web.dto.InvitationResponse;
import com.mapmory.backend.member.Member;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1")
public class GroupInvitationController {

    private final GroupInvitationService groupInvitationService;

    public GroupInvitationController(GroupInvitationService groupInvitationService) {
        this.groupInvitationService = groupInvitationService;
    }

    @PostMapping("/groups/{groupId}/invitations")
    public ResponseEntity<ApiResponse<InvitationResponse>> create(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId
    ) {
        InvitationResponse response = InvitationResponse.from(groupInvitationService.create(member.getId(), groupId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.from(response));
    }

    @GetMapping("/invitations/{tokenOrCode}")
    public ApiResponse<InvitationPreviewResponse> preview(
            @LoginMember Member member,
            @PathVariable String tokenOrCode
    ) {
        InvitationPreviewResponse response =
                InvitationPreviewResponse.from(groupInvitationService.preview(member.getId(), tokenOrCode));
        return ApiResponse.from(response);
    }

    @PostMapping("/invitations/{tokenOrCode}/accept")
    public ApiResponse<GroupResponse> accept(
            @LoginMember Member member,
            @PathVariable String tokenOrCode
    ) {
        return ApiResponse.from(GroupResponse.from(groupInvitationService.accept(member.getId(), tokenOrCode)));
    }
}
