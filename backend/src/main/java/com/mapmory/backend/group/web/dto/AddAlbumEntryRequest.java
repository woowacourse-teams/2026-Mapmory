package com.mapmory.backend.group.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddAlbumEntryRequest(@NotNull @Positive Long travelRecordId) {
}
