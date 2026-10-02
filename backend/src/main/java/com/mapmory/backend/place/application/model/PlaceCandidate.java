package com.mapmory.backend.place.application.model;

public record PlaceCandidate(
        String placeId, String name, String address,
        String attribution, String attributionUrl
) {
}
