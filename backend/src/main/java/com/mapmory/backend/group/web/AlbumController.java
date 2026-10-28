package com.mapmory.backend.group.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.group.application.AlbumEntryResult;
import com.mapmory.backend.group.application.AlbumQueryService;
import com.mapmory.backend.group.application.AlbumService;
import com.mapmory.backend.group.web.dto.AddAlbumEntryRequest;
import com.mapmory.backend.group.web.dto.AlbumDetailResponse;
import com.mapmory.backend.group.web.dto.AlbumListResponse;
import com.mapmory.backend.group.web.dto.CreateAlbumRequest;
import com.mapmory.backend.group.web.dto.UpdateAlbumRequest;
import com.mapmory.backend.member.Member;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/groups/{groupId}/albums")
public class AlbumController {

    private final AlbumService albumService;
    private final AlbumQueryService albumQueryService;

    public AlbumController(AlbumService albumService, AlbumQueryService albumQueryService) {
        this.albumService = albumService;
        this.albumQueryService = albumQueryService;
    }

    /**
     * 최근에 기록이 담긴 앨범이 먼저다. size는 1~50이다(기록 목록과 같다).
     */
    @GetMapping
    public ApiResponse<AlbumListResponse> list(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.from(AlbumListResponse.from(albumQueryService.list(member.getId(), groupId, page, size)));
    }

    @GetMapping("/{albumId}")
    public ApiResponse<AlbumDetailResponse> detail(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @PathVariable @Positive Long albumId
    ) {
        return ApiResponse.from(AlbumDetailResponse.from(albumQueryService.detail(member.getId(), groupId, albumId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AlbumDetailResponse>> open(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @Valid @RequestBody CreateAlbumRequest request
    ) {
        AlbumDetailResponse response = AlbumDetailResponse.from(albumService.open(
                member.getId(), groupId, request.travelRecordId(), request.name(), request.regionId()));
        return ResponseEntity.created(URI.create("/api/v1/groups/" + groupId + "/albums/" + response.id()))
                .body(ApiResponse.from(response));
    }

    /**
     * 처음 담으면 201, 같은 기록을 다시 보내면 200이다. 응답을 못 받은 앱이 그대로 다시 보내도 된다.
     */
    @PostMapping("/{albumId}/entries")
    public ResponseEntity<ApiResponse<AlbumDetailResponse>> addEntry(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @PathVariable @Positive Long albumId,
            @Valid @RequestBody AddAlbumEntryRequest request
    ) {
        AlbumEntryResult result = albumService.addEntry(member.getId(), groupId, albumId, request.travelRecordId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.from(AlbumDetailResponse.from(result.detail())));
    }

    @PatchMapping("/{albumId}")
    public ApiResponse<AlbumDetailResponse> update(
            @LoginMember Member member,
            @PathVariable @Positive Long groupId,
            @PathVariable @Positive Long albumId,
            @Valid @RequestBody UpdateAlbumRequest request
    ) {
        return ApiResponse.from(AlbumDetailResponse.from(albumService.update(
                member.getId(), groupId, albumId, request.name(), request.regionId())));
    }
}
