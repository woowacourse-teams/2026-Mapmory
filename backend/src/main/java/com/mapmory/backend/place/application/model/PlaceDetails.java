package com.mapmory.backend.place.application.model;

public record PlaceDetails(
        String placeId, String name, String countryCode, double latitude, double longitude,
        String attribution, String attributionUrl
) {
}
