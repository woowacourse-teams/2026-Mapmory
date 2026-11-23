package com.mapmory.backend.place.web;

import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.common.exception.FieldErrorDetail;
import com.mapmory.backend.place.application.LocationRegionService;
import com.mapmory.backend.place.web.dto.LocationRegionRequest;
import com.mapmory.backend.place.web.dto.LocationRegionResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
    private final ProblemDetailFactory problemDetailFactory;

    public PlaceLocationController(LocationRegionService locationRegionService,
                                   ProblemDetailFactory problemDetailFactory) {
        this.locationRegionService = locationRegionService;
        this.problemDetailFactory = problemDetailFactory;
    }

    @PostMapping("/api/v1/places/location")
    public ResponseEntity<ApiResponse<LocationRegionResponse>> locate(@Valid @RequestBody LocationRegionRequest request) {
        return ResponseEntity.ok(ApiResponse.from(
                LocationRegionResponse.from(locationRegionService.locate(request.toCoordinate()))));
    }

    /**
     * 본문이 비었거나 JSON·숫자가 아니면 400이다. 공통 처리기(500)로 넘기면 파싱 오류 메시지에 담긴 좌표 글자가
     * 원인 예외와 함께 ERROR 로그에 남으므로, 여기서 원인 없이 답한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> unreadableBody(HttpServletRequest request) {
        return problemDetailFactory.validation(
                List.of(new FieldErrorDetail("body", "위도·경도를 숫자로 보내 주세요.")), request);
    }
}
