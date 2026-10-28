package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagErrorCode;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "travel_record")
public class TravelRecord extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Embedded
    private RecordedPlace place;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Embedded
    private TravelPeriod period;

    // 사진과 태그 연결은 이 일지와 생명주기가 같은 애그리거트 내부 엔티티다. 그래서 cascade는 여기에만 둔다.
    // 새 자식은 함께 저장하고(PERSIST), 컬렉션에서 빠지면 지운다(orphanRemoval). 다른 cascade 동작은 쓰지 않는다.
    // 일지를 지울 때 자식은 DB의 ON DELETE CASCADE(V5, V16)가 지운다. 없으면 Hibernate가 자식마다 DELETE를 낸다.
    @OneToMany(mappedBy = "travelRecord", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("sortOrder ASC")
    private List<RecordMedia> media = new ArrayList<>();

    @OneToMany(mappedBy = "travelRecord", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private List<TravelRecordTag> tags = new ArrayList<>();

    protected TravelRecord() {
    }

    private TravelRecord(
            Member member,
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today
    ) {
        this.member = member;
        this.region = validateRegion(region);
        this.title = normalizeTitle(title);
        this.content = normalizeContent(content);
        this.period = periodNotAfter(startDate, endDate, today);
    }

    /**
     * today는 한국 기준 오늘({@code ServiceTime.today})이다. 기간이 그보다 뒤면 만들지 않는다.
     */
    public static TravelRecord of(
            Member member,
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today
    ) {
        return new TravelRecord(member, region, title, content, startDate, endDate, today);
    }

    public void update(
            Region region,
            String title,
            String content,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today
    ) {
        // 검증이 실패하면 아무 필드도 바뀌지 않도록 먼저 검증한다.
        Region validRegion = validateRegion(region);
        TravelPeriod validPeriod = periodNotAfter(startDate, endDate, today);
        this.region = validRegion;
        this.title = normalizeTitle(title);
        this.content = normalizeContent(content);
        this.period = validPeriod;
    }

    /**
     * 대한민국 일지는 시·도나 시·군·구에 붙인다. 부산광역시처럼 시·도까지만 골라도 되지만
     * 대한민국 전체에는 붙이지 않는다. 지도와 통계가 시·도부터 칠하기 때문이다.
     */
    private static Region validateRegion(Region region) {
        if (region.isCountry(Region.KOREA)) {
            throw new BusinessException(TravelRecordErrorCode.REGION_REQUIRED,
                    "대한민국 여행은 시도나 시군구를 골라야 합니다.");
        }
        return region;
    }

    public void setPlace(RecordedPlace place) {
        this.place = place;
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
     * 여행 기간은 기준일(오늘)보다 뒤일 수 없다.
     *
     * <p>기준일이 지나도 저장된 기간은 유효하므로 {@link TravelPeriod}가 생성 시점에 검사하지 않는다.
     * 생성({@link #of})과 수정({@link #update})이 이 규칙을 지킨다. 정적 메서드로도 열어 두는 것은
     * 응용 서비스가 외부 호출 전에 먼저 거절할 수 있게 하기 위해서다. ({@link #validateObjectKeys}와 같다)
     */
    public static void validatePeriod(LocalDate startDate, LocalDate endDate, LocalDate today) {
        periodNotAfter(startDate, endDate, today);
    }

    private static TravelPeriod periodNotAfter(LocalDate startDate, LocalDate endDate, LocalDate today) {
        TravelPeriod period = TravelPeriod.of(startDate, endDate);
        period.validateNotAfter(today);
        return period;
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
     * 이 일지의 태그를 requested로 바꾼다. found는 응용 서비스가 requested로 조회한 태그다.
     *
     * <p>모든 id가 이 일지 회원의 태그여야 한다. 없는 태그와 남의 태그는 구분하지 않는다.
     * 이미 붙은 태그는 그대로 두고 빠진 것만 떼고 새것만 붙인다. 한꺼번에 비우고 다시 붙이면
     * Hibernate가 INSERT를 DELETE보다 먼저 내보내, 남아야 할 태그가 UNIQUE(tag_id, travel_record_id)에 걸린다.
     * ({@link #synchronizeMedia}와 같은 이유)
     */
    public void retag(RecordTagIds requested, List<Tag> found) {
        Map<Long, Tag> ownedById = found.stream()
                .filter(tag -> tag.isOwnedBy(member.getId()))
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        if (!ownedById.keySet().containsAll(requested.values())) {
            throw new BusinessException(TagErrorCode.TAG_NOT_FOUND);
        }

        tags.removeIf(link -> !requested.contains(link.tagId()));
        Set<Long> linkedTagIds = tags.stream()
                .map(TravelRecordTag::tagId)
                .collect(Collectors.toSet());
        requested.values().stream()
                .filter(tagId -> !linkedTagIds.contains(tagId))
                .map(ownedById::get)
                .forEach(tag -> tags.add(TravelRecordTag.of(this, tag)));
    }

    /**
     * 화면에 보일 태그는 루트 리포지토리가 만든 순서로 읽는다. 이 목록은 애그리거트 안에서만 쓴다.
     */
    List<TravelRecordTag> getTags() {
        return List.copyOf(tags);
    }

    private static String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private static String normalizeTitle(String title) {
        return title == null || title.isBlank() ? "" : title;
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

    public RecordedPlace getPlace() {
        return place;
    }

    public LocalDate getStartDate() {
        return period.startDate();
    }

    public LocalDate getEndDate() {
        return period.endDate();
    }
}
