package com.mapmory.backend.group.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.group.application.GroupService;
import com.mapmory.backend.group.web.dto.GroupNameRequest;
import com.mapmory.backend.group.web.dto.GroupResponse;
import com.mapmory.backend.group.web.dto.GroupSummaryResponse;
import com.mapmory.backend.member.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<GroupResponse>> create(
            @LoginMember Member member,
            @Valid @RequestBody GroupNameRequest request
    ) {
        GroupResponse response = GroupResponse.from(groupService.create(member.getId(), request.name()));
        return ResponseEntity.created(URI.create("/api/v1/groups/" + response.id()))
                .body(ApiResponse.from(response));
    }

    @GetMapping
    public ApiResponse<List<GroupSummaryResponse>> findMine(@LoginMember Member member) {
        return ApiResponse.from(groupService.findMine(member.getId()).stream()
                .map(GroupSummaryResponse::from)
                .toList());
    }

    @GetMapping("/{groupId}")
    public ApiResponse<GroupResponse> get(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId
    ) {
        return ApiResponse.from(GroupResponse.from(groupService.get(member.getId(), groupId)));
    }

    @PatchMapping("/{groupId}")
    public ApiResponse<GroupResponse> rename(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @Valid @RequestBody GroupNameRequest request
    ) {
        return ApiResponse.from(GroupResponse.from(groupService.rename(member.getId(), groupId, request.name())));
    }
}
