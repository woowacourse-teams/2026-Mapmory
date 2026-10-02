package com.mapmory.backend.place.web.dto;

import com.mapmory.backend.place.application.model.PlaceCandidate;

public record PlaceCandidateResponse(
        String placeId, String name, String address,
        String attribution, String attributionUrl
) {
    public static PlaceCandidateResponse from(PlaceCandidate candidate) {
        return new PlaceCandidateResponse(candidate.placeId(), candidate.name(), candidate.address(),
                candidate.attribution(), candidate.attributionUrl());
    }
}
