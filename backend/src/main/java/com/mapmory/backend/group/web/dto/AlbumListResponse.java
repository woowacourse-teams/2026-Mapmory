package com.mapmory.backend.group.web.dto;

import com.mapmory.backend.group.application.AlbumPage;
import java.util.List;

public record AlbumListResponse(
        List<AlbumCardResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static AlbumListResponse from(AlbumPage page) {
        return new AlbumListResponse(
                page.items().stream().map(AlbumCardResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasNext()
        );
    }
}
