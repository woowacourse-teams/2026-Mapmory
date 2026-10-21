package com.mapmory.backend.travelrecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 일지 목록 한 페이지에서 화면에 필요한 값만 옮겨 담은 결과다. 캐시에 그대로 넣는다.
 *
 * <p>JPA 엔티티를 담지 않아 트랜잭션이 끝난 뒤에도 안전하게 읽힌다. 썸네일은 objectKey만 담고
 * 5분짜리 서명 URL은 담지 않는다. URL은 응답할 때마다 새로 만든다.
 */
public record TravelRecordListPage(
        List<Entry> entries,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public TravelRecordListPage {
        entries = List.copyOf(entries);
    }

    public record Entry(
            Long id,
            String title,
            String regionName,
            String placeName,
            String placeAttribution,
            String placeAttributionUrl,
            LocalDate startDate,
            LocalDate endDate,
            String thumbnailObjectKey,
            int photoCount,
            List<TagLabel> tags
    ) {
        public Entry {
            tags = List.copyOf(tags);
        }
    }

    public record TagLabel(Long id, String name) {
    }
}
