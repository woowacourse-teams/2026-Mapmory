package com.mapmory.backend.region.web;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.region.application.RegionBoundaryService;
import com.mapmory.backend.region.web.dto.RegionBoundaryResponse;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/regions")
public class RegionController {

    // 경계는 데이터 버전이 바뀔 때만 달라진다. 앱은 응답의 version으로 저장해 둔 경계를 바꿀지 정한다.
    private static final CacheControl BOUNDARY_CACHE = CacheControl.maxAge(Duration.ofDays(30)).cachePrivate();

    private final RegionBoundaryService boundaryService;

    public RegionController(RegionBoundaryService boundaryService) {
        this.boundaryService = boundaryService;
    }

    @GetMapping("/{countryCode}/boundary")
    public ResponseEntity<ApiResponse<RegionBoundaryResponse>> countryBoundary(
            @PathVariable @Pattern(regexp = "[A-Z]{2}") String countryCode
    ) {
        return ResponseEntity.ok()
                .cacheControl(BOUNDARY_CACHE)
                .body(ApiResponse.from(RegionBoundaryResponse.from(boundaryService.countryBoundary(countryCode))));
    }
}
