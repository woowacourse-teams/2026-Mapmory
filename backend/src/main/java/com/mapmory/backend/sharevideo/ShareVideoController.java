package com.mapmory.backend.sharevideo;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/share-videos")
public class ShareVideoController {

    private final ShareVideoService shareVideoService;

    public ShareVideoController(ShareVideoService shareVideoService) {
        this.shareVideoService = shareVideoService;
    }

    @PostMapping("/travel-timeline")
    public ResponseEntity<TravelRecordResponse<CreateShareVideoResponse>> create(@LoginMember Member member) {
        final ShareVideoJob job = shareVideoService.createTravelTimeline(member);
        final CreateShareVideoResponse response = CreateShareVideoResponse.from(job);

        return ResponseEntity.accepted()
                .body(TravelRecordResponse.of(response));
    }
}
