package com.mapmory.backend.tag.dto;

import com.mapmory.backend.tag.Tag;

public record TagSummaryResponse(Long id, String name) {
    public static TagSummaryResponse from(Tag tag) {
        return new TagSummaryResponse(tag.getId(), tag.getName());
    }
}
