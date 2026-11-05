package com.mapmory.backend.place.web;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.place.application.LocationRegionService;
import com.mapmory.backend.place.web.dto.LocationRegionRequest;
import com.mapmory.backend.place.web.dto.LocationRegionResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사진 좌표로 기록 지역을 추천한다. 회원 한도는 장소 선택과 같은 한도(/api/v1/places/*)를 쓴다.
 * 좌표는 GET 쿼리가 아니라 POST 본문으로 받아 URL 로그에 남지 않게 한다.
 */
@RestController
public class PlaceLocationController {

    private final LocationRegionService locationRegionService;

    public PlaceLocationController(LocationRegionService locationRegionService) {
        this.locationRegionService = locationRegionService;
    }

    @PostMapping("/api/v1/places/location")
    public ResponseEntity<ApiResponse<LocationRegionResponse>> locate(@Valid @RequestBody LocationRegionRequest request) {
        return ResponseEntity.ok(ApiResponse.from(
                LocationRegionResponse.from(locationRegionService.locate(request.toCoordinate()))));
    }
}
