package com.mapmory.backend.travelrecord.web;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.application.TravelRecordDetail;
import com.mapmory.backend.travelrecord.application.TravelRecordService;
import com.mapmory.backend.travelrecord.application.TravelRecordSummaries;
import com.mapmory.backend.travelrecord.domain.TravelRecord;
import com.mapmory.backend.travelrecord.web.dto.CreateTravelRecordResponse;
import com.mapmory.backend.travelrecord.web.dto.TravelRecordDetailResponse;
import com.mapmory.backend.travelrecord.web.dto.TravelRecordListResponse;
import com.mapmory.backend.travelrecord.web.dto.TravelRecordRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1")
public class TravelRecordController {

    private final TravelRecordService travelRecordService;

    public TravelRecordController(TravelRecordService travelRecordService) {
        this.travelRecordService = travelRecordService;
    }

    @PostMapping("/travel-records")
    public ResponseEntity<ApiResponse<CreateTravelRecordResponse>> create(
            @LoginMember Member member,
            @Valid @RequestBody TravelRecordRequest travelRecordRequest
    ) {
        TravelRecord travelRecord = travelRecordService.create(member, travelRecordRequest.toCommand());
        CreateTravelRecordResponse response = CreateTravelRecordResponse.from(travelRecord);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.from(response));
    }

    @GetMapping("/travel-records")
    public ResponseEntity<ApiResponse<TravelRecordListResponse>> findAll(
            @LoginMember Member member,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z]{2}") String countryCode,
            @RequestParam(required = false) @Pattern(regexp = "\\S{1,20}") String provinceCode,
            @RequestParam(required = false) @Pattern(regexp = "\\S{1,20}") String districtCode,
            @RequestParam(required = false) @Positive Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        TravelRecordSummaries summaries = travelRecordService.findAll(
                member,
                countryCode,
                provinceCode,
                districtCode,
                tagId,
                page,
                size
        );
        TravelRecordListResponse response = TravelRecordListResponse.from(summaries);

        return ResponseEntity.ok(ApiResponse.from(response));
    }

    @GetMapping("/travel-records/{travelRecordId}")
    public ResponseEntity<ApiResponse<TravelRecordDetailResponse>> findById(
            @LoginMember Member member,
            @PathVariable @Positive Long travelRecordId
    ) {
        TravelRecordDetailResponse response = TravelRecordDetailResponse.from(
                travelRecordService.findById(member, travelRecordId)
        );

        return ResponseEntity.ok(ApiResponse.from(response));
    }

    @PutMapping("/travel-records/{travelRecordId}")
    public ResponseEntity<ApiResponse<TravelRecordDetailResponse>> update(
            @LoginMember Member member,
            @PathVariable @Positive Long travelRecordId,
            @Valid @RequestBody TravelRecordRequest travelRecordRequest
    ) {
        TravelRecordDetail detail = travelRecordService.update(
                member,
                travelRecordId,
                travelRecordRequest.toCommand()
        );
        TravelRecordDetailResponse response = TravelRecordDetailResponse.from(detail);

        return ResponseEntity.ok(ApiResponse.from(response));
    }

    @DeleteMapping("/travel-records/{travelRecordId}")
    public ResponseEntity<Void> delete(
            @LoginMember Member member,
            @PathVariable @Positive Long travelRecordId
    ) {
        travelRecordService.delete(member, travelRecordId);

        return ResponseEntity.noContent().build();
    }
}
