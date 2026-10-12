package com.mapmory.backend.place.web;

import com.mapmory.backend.place.application.PlaceSearchService;
import com.mapmory.backend.place.application.PlaceSelectionService;
import com.mapmory.backend.place.web.dto.PlaceCandidateResponse;
import com.mapmory.backend.place.web.dto.PlaceSelectionResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/places")
public class PlaceController {

    // 클라이언트가 검색을 시작할 때 만든 UUID. Google은 URL-safe base64 36자 이하만 받는다.
    private static final String SESSION_TOKEN = "[A-Za-z0-9_-]{1,36}";

    private final PlaceSearchService placeSearchService;
    private final PlaceSelectionService placeSelectionService;

    public PlaceController(PlaceSearchService placeSearchService, PlaceSelectionService placeSelectionService) {
        this.placeSearchService = placeSearchService;
        this.placeSelectionService = placeSelectionService;
    }

    @GetMapping("/search")
    public ResponseEntity<TravelRecordResponse<List<PlaceCandidateResponse>>> search(
            @AuthenticationPrincipal Long memberId,
            @RequestParam @NotBlank @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = SESSION_TOKEN) String sessionToken
    ) {
        return ResponseEntity.ok(TravelRecordResponse.of(
                placeSearchService.search(memberId, query, sessionToken).stream().map(PlaceCandidateResponse::from).toList()));
    }

    @GetMapping("/{placeId}")
    public ResponseEntity<TravelRecordResponse<PlaceSelectionResponse>> select(
            @AuthenticationPrincipal Long memberId,
            @PathVariable String placeId,
            @RequestParam(required = false) @Pattern(regexp = SESSION_TOKEN) String sessionToken
    ) {
        return ResponseEntity.ok(TravelRecordResponse.of(
                PlaceSelectionResponse.from(placeSelectionService.select(memberId, placeId, sessionToken))));
    }
}
