package com.mapmory.backend.travelrecord.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.application.TravelRecordDetail;
import com.mapmory.backend.travelrecord.application.TravelRecordService;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.web.dto.CreateTravelRecordResponse;
import com.mapmory.backend.travelrecord.web.dto.TravelRecordDetailResponse;
import com.mapmory.backend.travelrecord.web.dto.TravelRecordV2Request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 일지 쓰기 v2. 사진을 키와 촬영일을 묶은 객체로 받는다. 응답은 v1과 같다.
 *
 * <p>조회와 삭제는 v1만 있다. 응답에 필드가 늘어나는 변경은 예전 앱이 무시하므로 버전을 나누지 않는다. (ADR 0030)
 */
@RestController
@Validated
@RequestMapping("/api/v2")
public class TravelRecordV2Controller {

    private final TravelRecordService travelRecordService;

    public TravelRecordV2Controller(TravelRecordService travelRecordService) {
        this.travelRecordService = travelRecordService;
    }

    @PostMapping("/travel-records")
    public ResponseEntity<ApiResponse<CreateTravelRecordResponse>> create(
            @LoginMember Member member,
            @Valid @RequestBody TravelRecordV2Request request
    ) {
        TravelRecord travelRecord = travelRecordService.create(member, request.toCommand());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.from(CreateTravelRecordResponse.from(travelRecord)));
    }

    @PutMapping("/travel-records/{travelRecordId}")
    public ResponseEntity<ApiResponse<TravelRecordDetailResponse>> update(
            @LoginMember Member member,
            @PathVariable @Positive Long travelRecordId,
            @Valid @RequestBody TravelRecordV2Request request
    ) {
        TravelRecordDetail detail = travelRecordService.update(member, travelRecordId, request.toCommand());

        return ResponseEntity.ok(ApiResponse.from(TravelRecordDetailResponse.from(detail)));
    }
}
