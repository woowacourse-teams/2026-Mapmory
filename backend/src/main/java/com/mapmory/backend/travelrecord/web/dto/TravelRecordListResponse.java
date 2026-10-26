package com.mapmory.backend.travelrecord.web.dto;

import com.mapmory.backend.travelrecord.application.TravelRecordListPage;
import com.mapmory.backend.travelrecord.application.TravelRecordSummaries;
import java.util.List;

public record TravelRecordListResponse(
        List<TravelRecordListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static TravelRecordListResponse from(TravelRecordSummaries summaries) {
        TravelRecordListPage listPage = summaries.listPage();

        return new TravelRecordListResponse(
                listPage.entries().stream()
                        .map(entry -> TravelRecordListItemResponse.from(entry, summaries.thumbnailUrlOf(entry)))
                        .toList(),
                listPage.page(),
                listPage.size(),
                listPage.totalElements(),
                listPage.totalPages(),
                listPage.hasNext()
        );
    }
}
