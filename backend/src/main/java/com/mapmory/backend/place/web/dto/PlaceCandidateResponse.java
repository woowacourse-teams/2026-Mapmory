package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.domain.PlaceCandidate;

public record PlaceCandidateResponse(
        String placeId, String name, String address,
        String attribution, String attributionUrl
) {
    public static PlaceCandidateResponse from(PlaceCandidate candidate) {
        return new PlaceCandidateResponse(candidate.id().value(), candidate.name(), candidate.address(),
                candidate.provider().attribution(), candidate.provider().attributionUrl());
    }
}
