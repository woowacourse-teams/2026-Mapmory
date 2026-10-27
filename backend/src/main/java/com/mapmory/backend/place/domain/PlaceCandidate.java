package com.mapmory.backend.place.domain;

public record PlaceCandidate(
        String placeId, String name, String address,
        String attribution, String attributionUrl
) {
}
