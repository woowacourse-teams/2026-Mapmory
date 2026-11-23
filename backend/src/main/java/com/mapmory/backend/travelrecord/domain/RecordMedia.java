package com.mapmory.backend.travelrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "record_media")
public class RecordMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "travel_record_id", nullable = false)
    private TravelRecord travelRecord;

    @Embedded
    private ObjectKey objectKey;

    /**
     * 썸네일 워커가 자기 UPDATE로만 채운다({@code TravelRecordRepository#attachThumbnail}). 엔티티 변경(순서, 촬영일)이
     * 트랜잭션 시작 때 읽은 빈 값으로 워커가 막 채운 키를 덮지 않도록 엔티티 UPDATE에서는 빼 둔다.
     */
    @Column(name = "thumb_key", length = 500, updatable = false)
    private String thumbKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /**
     * 사진 촬영일. 앱이 사진첩에서 읽어 보낸다. 모르면 null이다. 업로드 시각({@link #createdAt})과는 다르다.
     */
    @Column(name = "captured_date")
    private LocalDate capturedDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RecordMedia() {
    }

    private RecordMedia(TravelRecord travelRecord, ObjectKey objectKey, String thumbKey, int sortOrder) {
        this.travelRecord = travelRecord;
        this.objectKey = objectKey;
        this.thumbKey = thumbKey;
        this.sortOrder = sortOrder;
    }

    public static RecordMedia of(TravelRecord travelRecord, String objectKey, String thumbKey, int sortOrder) {
        return new RecordMedia(travelRecord, ObjectKey.from(objectKey), thumbKey, sortOrder);
    }

    /**
     * 애그리거트 안에서 쓰는 생성 경로. 키는 {@link ObjectKeys}가 이미 검증했다.
     */
    static RecordMedia of(TravelRecord travelRecord, ObjectKey objectKey, int sortOrder) {
        return new RecordMedia(travelRecord, objectKey, null, sortOrder);
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    /**
     * 촬영일을 받았을 때만 바꾼다. 촬영일을 보내지 않는 예전 앱이 수정해도 이미 저장된 촬영일을 지우지 않는다.
     */
    void recordCapturedDate(LocalDate capturedDate) {
        if (capturedDate != null) {
            this.capturedDate = capturedDate;
        }
    }

    public LocalDate getCapturedDate() {
        return capturedDate;
    }

    public Long getId() {
        return id;
    }

    ObjectKey objectKey() {
        return objectKey;
    }

    public String getObjectKey() {
        return objectKey.value();
    }

    public Long travelRecordId() {
        return travelRecord.getId();
    }

    /**
     * S3에 있는 이 사진의 객체들. 썸네일이 따로 있으면 함께 준다.
     */
    List<String> storedKeys() {
        if (thumbKey == null || thumbKey.isBlank()) {
            return List.of(getObjectKey());
        }
        return List.of(getObjectKey(), thumbKey);
    }

    public String getThumbnailObjectKey() {
        if (thumbKey == null || thumbKey.isBlank()) {
            return getObjectKey();
        }
        return thumbKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
