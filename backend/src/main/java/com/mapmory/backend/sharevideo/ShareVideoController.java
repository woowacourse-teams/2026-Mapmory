package com.mapmory.backend.sharevideo;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.dto.CreateShareVideoResponse;
import com.mapmory.backend.sharevideo.dto.ShareVideoDownloadResponse;
import com.mapmory.backend.sharevideo.dto.ShareVideoJobResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/share-videos")
public class ShareVideoController {

    private final ShareVideoService shareVideoService;

    public ShareVideoController(ShareVideoService shareVideoService) {
        this.shareVideoService = shareVideoService;
    }

    @PostMapping("/travel-timeline")
    public ResponseEntity<ApiResponse<CreateShareVideoResponse>> create(@LoginMember Member member) {
        final ShareVideoJob job = shareVideoService.createTravelTimeline(member);
        final CreateShareVideoResponse response = CreateShareVideoResponse.from(job);

        return ResponseEntity.accepted()
                .body(ApiResponse.from(response));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<ShareVideoJobResponse>> getJob(
            @LoginMember final Member member,
            @PathVariable @Positive final Long jobId
    ) {
        final ShareVideoJob job = shareVideoService.getJob(member, jobId);

        return ResponseEntity.ok(
                ApiResponse.from(ShareVideoJobResponse.from(job))
        );
    }

    @GetMapping("/{jobId}/download-url")
    public ResponseEntity<ApiResponse<ShareVideoDownloadResponse>> getDownloadUrl(
            @LoginMember final Member member,
            @PathVariable @Positive final Long jobId
    ) {
        final ShareVideoDownloadUrl downloadUrl = shareVideoService.createDownloadUrl(member, jobId);

        return ResponseEntity.ok(
                ApiResponse.from(
                        ShareVideoDownloadResponse.of(
                                jobId,
                                downloadUrl
                        )
                )
        );
    }
}
