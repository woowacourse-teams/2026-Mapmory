package com.mapmory.backend.tag;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.tag.dto.TagRequest;
import com.mapmory.backend.tag.dto.TagResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/tags")
public class TagController {
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TagResponse>> create(
            @LoginMember Member member,
            @Valid @RequestBody TagRequest request
    ) {
        TagResponse response = TagResponse.from(tagService.create(member, request.name()));
        return ResponseEntity.created(URI.create("/api/v1/tags/" + response.id()))
                .body(ApiResponse.from(response));
    }

    @GetMapping
    public ApiResponse<List<TagResponse>> findAll(@LoginMember Member member) {
        List<TagResponse> allTagsResponse = tagService.findAll(member).stream()
                .map(TagResponse::from)
                .toList();

        return ApiResponse.from(allTagsResponse);
    }

    @PatchMapping("/{tagId}")
    public ApiResponse<TagResponse> update(
            @LoginMember Member member,
            @PathVariable @Positive Long tagId,
            @Valid @RequestBody TagRequest request
    ) {
        Tag update = tagService.update(member, tagId, request.name());
        return ApiResponse.from(TagResponse.from(update));
    }

    @DeleteMapping("/{tagId}")
    public ResponseEntity<Void> delete(
            @LoginMember Member member,
            @PathVariable @Positive Long tagId
    ) {
        tagService.delete(member, tagId);
        return ResponseEntity.noContent().build();
    }
}
