package com.mapmory.backend.group.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;

/**
 * Album 애그리거트 안쪽 엔티티. 앨범에 담긴 멤버 기록 하나를 가리킨다.
 *
 * <p>여행 기록은 다른 컨텍스트라 id로만 가리킨다. 기록이 지워지면 여행기록의 이벤트를 받아 이 항목을 지운다.
 */
@Entity
@Table(name = "album_entry")
public class AlbumEntry extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "album_id", nullable = false)
    private Album album;

    @Column(name = "travel_record_id", nullable = false)
    private Long travelRecordId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    protected AlbumEntry() {
    }

    AlbumEntry(Album album, Long travelRecordId, Long memberId) {
        this.album = Objects.requireNonNull(album, "album must not be null");
        this.travelRecordId = Objects.requireNonNull(travelRecordId, "travelRecordId must not be null");
        this.memberId = Objects.requireNonNull(memberId, "memberId must not be null");
    }

    public Long getId() {
        return id;
    }

    public Long getTravelRecordId() {
        return travelRecordId;
    }

    public Long getMemberId() {
        return memberId;
    }
}
