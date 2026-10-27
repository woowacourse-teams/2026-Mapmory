package com.mapmory.backend.sharevideo;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.sharevideo.dto.CreateShareVideoResponse;
import com.mapmory.backend.sharevideo.dto.CreateShareVideoRequest;
import com.mapmory.backend.sharevideo.dto.ShareVideoDownloadResponse;
import com.mapmory.backend.sharevideo.dto.ShareVideoJobResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
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
    public ResponseEntity<TravelRecordResponse<CreateShareVideoResponse>> create(
            @LoginMember final Member member,
            @RequestBody(required = false) final CreateShareVideoRequest request
    ) {
        final ShareVideoStyle styleId;
        if (request == null) {
            styleId = ShareVideoStyle.defaultStyle();
        } else {
            styleId = request.resolvedStyle();
        }
        final ShareVideoJob job = shareVideoService.createTravelTimeline(member, styleId);
        final CreateShareVideoResponse response = CreateShareVideoResponse.from(job);

        return ResponseEntity.accepted()
                .body(TravelRecordResponse.of(response));
    }

    public ResponseEntity<TravelRecordResponse<CreateShareVideoResponse>> create(
            final Member member
    ) {
        return create(member, null);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<TravelRecordResponse<ShareVideoJobResponse>> getJob(
            @LoginMember final Member member,
            @PathVariable @Positive final Long jobId
    ) {
        final ShareVideoJob job = shareVideoService.getJob(member, jobId);

        return ResponseEntity.ok(
                TravelRecordResponse.of(ShareVideoJobResponse.from(job))
        );
    }

    @GetMapping("/{jobId}/download-url")
    public ResponseEntity<TravelRecordResponse<ShareVideoDownloadResponse>> getDownloadUrl(
            @LoginMember final Member member,
            @PathVariable @Positive final Long jobId
    ) {
        final ShareVideoDownloadUrl downloadUrl = shareVideoService.createDownloadUrl(member, jobId);

        return ResponseEntity.ok(
                TravelRecordResponse.of(
                        ShareVideoDownloadResponse.of(
                                jobId,
                                downloadUrl
                        )
                )
        );
    }
}
