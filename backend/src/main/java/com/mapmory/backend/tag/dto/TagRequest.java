package com.mapmory.backend.tag.dto;

import jakarta.validation.constraints.NotNull;

public record TagRequest(@NotNull String name) {
}
