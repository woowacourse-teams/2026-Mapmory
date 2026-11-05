package com.mapmory.backend.region.web;

import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.region.application.RegionBoundaryService;
import com.mapmory.backend.region.domain.RegionBoundary;
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

    // 경계는 데이터 버전이 바뀔 때만 달라진다. 앱은 경계와 ETag를 저장해 두고 If-None-Match로 다시 물어,
    // 같으면 본문 없는 304를 받는다(ResponseEntity에 ETag가 있으면 Spring이 비교해 304로 바꾼다).
    private static final CacheControl BOUNDARY_CACHE = CacheControl.maxAge(Duration.ofDays(1)).cachePrivate();

    private final RegionBoundaryService boundaryService;

    public RegionController(RegionBoundaryService boundaryService) {
        this.boundaryService = boundaryService;
    }

    @GetMapping("/{countryCode}/boundary")
    public ResponseEntity<ApiResponse<RegionBoundaryResponse>> countryBoundary(
            @PathVariable @Pattern(regexp = "[A-Z]{2}") String countryCode
    ) {
        RegionBoundary boundary = boundaryService.countryBoundary(countryCode);
        return ResponseEntity.ok()
                .cacheControl(BOUNDARY_CACHE)
                .eTag(boundary.versionTag())
                .body(ApiResponse.from(RegionBoundaryResponse.from(boundary)));
    }
}
