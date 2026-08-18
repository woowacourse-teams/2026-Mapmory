package com.mapmory.backend.travelrecord;

import com.mapmory.backend.auth.security.LoginMemberId;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecord.dto.CreateTravelRecordResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordListResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class TravelRecordController {

    private final TravelRecordService travelRecordService;

    public TravelRecordController(TravelRecordService travelRecordService) {
        this.travelRecordService = travelRecordService;
    }

    @PostMapping("/travel-records")
    public ResponseEntity<TravelRecordResponse<CreateTravelRecordResponse>> create(
            @LoginMemberId Long memberId,
            @Valid @RequestBody TravelRecordRequest travelRecordRequest
    ) {
        TravelRecord travelRecord = travelRecordService.create(memberId, travelRecordRequest);
        CreateTravelRecordResponse response = CreateTravelRecordResponse.from(travelRecord);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TravelRecordResponse.of(response));
    }

    @GetMapping("/travel-records")
    public ResponseEntity<TravelRecordResponse<TravelRecordListResponse>> findAll(
            @LoginMemberId Long memberId,
            @RequestParam(required = false) String countryCode,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String districtCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<TravelRecord> travelRecords = travelRecordService.findAll(
                memberId,
                countryCode,
                provinceCode,
                districtCode,
                page,
                size
        );
        TravelRecordListResponse response = TravelRecordListResponse.from(travelRecords);

        return ResponseEntity.ok(TravelRecordResponse.of(response));
    }
}
