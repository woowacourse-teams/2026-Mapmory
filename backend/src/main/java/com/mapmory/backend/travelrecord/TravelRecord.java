package com.mapmory.backend.travelrecord;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.tag.TagErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "travel_record")
public class TravelRecord extends BaseEntity {
    private static final int MAX_TAGS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Embedded
    private TravelPeriod period;

    @OneToMany(mappedBy = "travelRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<RecordMedia> media = new ArrayList<>();

    protected TravelRecord() {
    }

    private TravelRecord(
            Member member,
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.member = member;
        this.region = region;
        this.title = title;
        this.content = normalizeContent(content);
        this.period = TravelPeriod.of(startDate, endDate);
    }

    public static TravelRecord of(
            Member member,
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new TravelRecord(member, region, title, content, startDate, endDate);
    }

    public void update(
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate
    ) {
        this.region = region;
        this.title = title;
        this.content = normalizeContent(content);
        this.period = TravelPeriod.of(startDate, endDate);
    }

    /**
     * 한 일지 안에서 같은 Object Key를 두 번 가질 수 없다.
     *
     * <p>일지를 만들기 전에도 검증할 수 있어야 해서 정적 메서드다.
     * 생성과 수정이 같은 시점에 같은 규칙을 적용한다.
     */
    public static void validateObjectKeys(List<String> objectKeys) {
        if (new HashSet<>(objectKeys).size() != objectKeys.size()) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
    }

    /**
     * 정렬 순서대로 정리한 이 일지의 미디어.
     */
    public List<RecordMedia> getMedia() {
        return media.stream()
                .sorted(Comparator.comparingInt(RecordMedia::getSortOrder))
                .toList();
    }

    /**
     * 요청된 Object Key 중 이 일지가 아직 가지고 있지 않은 것들.
     */
    public List<String> newObjectKeys(List<String> objectKeys) {
        Set<String> currentObjectKeys = media.stream()
                .map(RecordMedia::getObjectKey)
                .collect(Collectors.toSet());

        return objectKeys.stream()
                .filter(objectKey -> !currentObjectKeys.contains(objectKey))
                .toList();
    }

    /**
     * 요청된 Object Key 순서를 이 일지의 미디어 정렬 순서로 삼는다.
     * 이미 가진 미디어는 순서만 바꾸고, 새 키는 미디어를 만들며, 빠진 것은 컬렉션에서 떨어져
     * orphanRemoval로 삭제된다.
     */
    public void synchronizeMedia(List<String> objectKeys) {
        validateObjectKeys(objectKeys);

        Map<String, RecordMedia> unusedMedia = new LinkedHashMap<>();
        for (RecordMedia recordMedia : media) {
            unusedMedia.put(recordMedia.getObjectKey(), recordMedia);
        }

        List<RecordMedia> addedMedia = new ArrayList<>();
        for (int sortOrder = 0; sortOrder < objectKeys.size(); sortOrder++) {
            String objectKey = objectKeys.get(sortOrder);
            RecordMedia recordMedia = unusedMedia.remove(objectKey);
            if (recordMedia == null) {
                addedMedia.add(RecordMedia.of(this, objectKey, null, sortOrder));
            } else {
                recordMedia.updateSortOrder(sortOrder);
            }
        }

        media.removeAll(unusedMedia.values());
        media.addAll(addedMedia);
    }

    /**
     * 한 일지에 붙일 수 있는 태그는 최대 {@value #MAX_TAGS}개이며 중복될 수 없다.
     */
    public void validateTagIds(List<Long> tagIds) {
        if (tagIds.size() > MAX_TAGS) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAGS);
        }
        if (new HashSet<>(tagIds).size() != tagIds.size()) {
            throw new BusinessException(TagErrorCode.INVALID_TAG_IDS);
        }
    }

    private static String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Region getRegion() {
        return region;
    }

    public LocalDate getStartDate() {
        return period.startDate();
    }

    public LocalDate getEndDate() {
        return period.endDate();
    }
}
