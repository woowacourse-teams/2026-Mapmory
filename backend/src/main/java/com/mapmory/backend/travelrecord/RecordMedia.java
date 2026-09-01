package com.mapmory.backend.travelrecord;

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
import java.time.LocalDateTime;
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

    @Column(name = "thumb_key", length = 500)
    private String thumbKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected RecordMedia() {
    }

    private RecordMedia(TravelRecord travelRecord, String objectKey, String thumbKey, int sortOrder) {
        this.travelRecord = travelRecord;
        this.objectKey = ObjectKey.from(objectKey);
        this.thumbKey = thumbKey;
        this.sortOrder = sortOrder;
    }

    public static RecordMedia of(TravelRecord travelRecord, String objectKey, String thumbKey, int sortOrder) {
        return new RecordMedia(travelRecord, objectKey, thumbKey, sortOrder);
    }

    public void updateSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey.value();
    }

    public Long travelRecordId() {
        return travelRecord.getId();
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
