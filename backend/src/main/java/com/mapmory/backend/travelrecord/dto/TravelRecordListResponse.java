package com.mapmory.backend.travelrecord.dto;

import com.mapmory.backend.travelrecord.TravelRecord;
import com.mapmory.backend.travelrecord.TravelRecordSummaries;
import java.util.List;
import org.springframework.data.domain.Page;

public record TravelRecordListResponse(
        List<TravelRecordListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static TravelRecordListResponse from(TravelRecordSummaries summaries) {
        Page<TravelRecord> travelRecords = summaries.travelRecords();

        return new TravelRecordListResponse(
                travelRecords.getContent().stream()
                        .map(travelRecord -> TravelRecordListItemResponse.from(
                                travelRecord,
                                summaries.tagsOf(travelRecord),
                                summaries.thumbnailUrlOf(travelRecord)
                        ))
                        .toList(),
                travelRecords.getNumber(),
                travelRecords.getSize(),
                travelRecords.getTotalElements(),
                travelRecords.getTotalPages(),
                travelRecords.hasNext()
        );
    }
}
