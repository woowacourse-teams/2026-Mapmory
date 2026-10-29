package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.region.domain.Region;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 같은 여행 하나를 보여 주는 묶음. 저장은 각자 여행 기록에 하고, 앨범은 그 기록을 id로 가리키기만 한다.
 *
 * <p>이름과 지역은 앨범이 갖고 멤버 누구나 바꾼다. 기간은 담긴 기록들의 기간을 합쳐 계산하므로 갖지 않는다.
 * 지역은 앨범 카드와 붙이기 기본값에만 쓰고 지도를 칠하지 않는다. 지도는 각자 기록의 지역으로 칠한다.
 *
 * <p>지금은 그룹 안에만 있지만 나중에 개인 기록에도 쓸 수 있어 GroupAlbum이 아니라 Album이다.
 */
@Entity
@Table(name = "album")
public class Album extends BaseEntity {

    // album.name 컬럼 길이(VARCHAR(200))와 같다. 여행 기록 제목 길이와 맞췄다.
    private static final int MAX_NAME_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(name = "opener_member_id", nullable = false)
    private Long openerMemberId;

    // 항목은 이 앨범과 생명주기가 같은 애그리거트 내부 엔티티다. 새 항목은 함께 저장하고(PERSIST), 빠지면 지운다(orphanRemoval).
    // 앨범 삭제는 아직 없다. 생기면 album_entry FK에 ON DELETE CASCADE를 더하고 @OnDelete로 맡긴다(ADR 0017 cascade 규칙).
    @OneToMany(mappedBy = "album", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<AlbumEntry> entries = new ArrayList<>();

    protected Album() {
    }

    private Album(Long groupId, String name, Region region, Long openerMemberId) {
        this.groupId = Objects.requireNonNull(groupId, "groupId must not be null");
        this.name = validateName(name);
        this.region = Objects.requireNonNull(region, "region must not be null");
        this.openerMemberId = Objects.requireNonNull(openerMemberId, "openerMemberId must not be null");
    }

    /**
     * 새 앨범의 이름. 앱은 앨범을 따로 보여 주지 않아 이름을 받지 못할 수 있다.
     * 보낸 이름이 비었으면 첫 기록의 제목, 제목도 비었으면(제목은 선택이다) 앨범 지역의 이름이다.
     */
    public static String nameFor(String requestedName, String recordTitle, Region region) {
        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }
        if (recordTitle != null && !recordTitle.isBlank()) {
            return recordTitle;
        }
        return region.getName();
    }

    /**
     * 따로 올리기: 내 기록 하나로 새 앨범을 연다. 연 사람의 기록이 첫 항목이다.
     */
    public static Album open(Long groupId, String name, Region region, Long openerMemberId, Long travelRecordId) {
        Album album = new Album(groupId, name, region, openerMemberId);
        album.entries.add(new AlbumEntry(album, travelRecordId, openerMemberId));
        return album;
    }

    /**
     * 사진 붙이기: 내 기록을 이 앨범에 담는다. 멤버인지와 내 기록인지는 부르는 쪽이 먼저 확인한다.
     *
     * <p>같은 기록을 다시 담으면 아무것도 바꾸지 않고 false다. 응답을 못 받은 앱이 그대로 다시 보내도 되게 한다.
     * 한 멤버가 기록을 여럿 담을 수 있다. 지금 기록은 장소 하나라 여행 하나에 기록이 여럿이다(ADR 0022 2026-10-28 보완).
     *
     * @return 새로 담았으면 true
     */
    public boolean addEntry(Long memberId, Long travelRecordId) {
        if (travelRecordIds().contains(travelRecordId)) {
            return false;
        }
        entries.add(new AlbumEntry(this, travelRecordId, memberId));
        return true;
    }

    /**
     * 멤버 누구나 이름을 바꾼다. 멤버인지는 부르는 쪽이 먼저 확인한다.
     */
    public void rename(String name) {
        this.name = validateName(name);
    }

    /**
     * 멤버 누구나 지역을 바꾼다. 앨범 카드에만 쓰므로 담긴 기록의 지역은 그대로다.
     */
    public void changeRegion(Region region) {
        this.region = Objects.requireNonNull(region, "region must not be null");
    }

    /**
     * 지워진 기록의 항목을 뗀다. 떼어 낸 항목은 orphanRemoval로 지워진다.
     *
     * @return 뗀 항목이 있으면 true
     */
    public boolean removeEntriesOf(Long travelRecordId) {
        return entries.removeIf(entry -> entry.getTravelRecordId().equals(travelRecordId));
    }

    /**
     * 담긴 항목이 없다. 빈 앨범은 지운다(ADR 0022 2026-10-29 보완).
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 이 멤버가 담은 기록들. 담은 순서다.
     */
    public List<Long> travelRecordIdsOf(Long memberId) {
        return entries.stream()
                .filter(entry -> entry.getMemberId().equals(memberId))
                .map(AlbumEntry::getTravelRecordId)
                .toList();
    }

    public List<Long> travelRecordIds() {
        return entries.stream().map(AlbumEntry::getTravelRecordId).toList();
    }

    private static String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException(GroupErrorCode.INVALID_ALBUM_NAME);
        }
        String trimmed = name.strip();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_NAME_LENGTH) {
            throw new BusinessException(GroupErrorCode.INVALID_ALBUM_NAME);
        }
        return trimmed;
    }

    public Long getId() {
        return id;
    }

    public Long getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }

    public Region getRegion() {
        return region;
    }

    public Long getOpenerMemberId() {
        return openerMemberId;
    }

    public List<AlbumEntry> getEntries() {
        return List.copyOf(entries);
    }
}
