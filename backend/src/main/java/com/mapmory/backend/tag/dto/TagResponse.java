package com.mapmory.backend.tag.dto;

import com.mapmory.backend.tag.Tag;
import java.time.LocalDateTime;

public record TagResponse(Long id, String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getName(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
