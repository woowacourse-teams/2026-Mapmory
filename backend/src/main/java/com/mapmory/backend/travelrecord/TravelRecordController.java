package com.mapmory.backend.travelrecord;

import com.mapmory.backend.auth.security.LoginMemberId;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecord.dto.CreateTravelRecordResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/v1")
public class TravelRecordController {

    private TravelRecordService travelRecordService;

    public TravelRecordController(TravelRecordService travelRecordService) {
        this.travelRecordService = travelRecordService;
    }

    @PostMapping("/travel-records")
    public ResponseEntity<CreateTravelRecordResponse> create(@LoginMemberId Long memberId,
                                                             @Valid @RequestBody TravelRecordRequest travelRecordRequest
    ) {
        TravelRecord travelRecord = travelRecordService.create(memberId, travelRecordRequest);
        CreateTravelRecordResponse response = CreateTravelRecordResponse.from(travelRecord);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
