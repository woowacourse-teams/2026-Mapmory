package com.mapmory.backend.travelrecordtag;

import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.travelrecord.TravelRecord;
import jakarta.persistence.Column;
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
@Table(name = "travel_record_tag")
public class TravelRecordTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "travel_record_id", nullable = false)
    private TravelRecord travelRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tag_id", nullable = false)
    private Tag tag;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected TravelRecordTag() {
    }

    private TravelRecordTag(TravelRecord travelRecord, Tag tag) {
        this.travelRecord = travelRecord;
        this.tag = tag;
    }

    public static TravelRecordTag of(TravelRecord travelRecord, Tag tag) {
        return new TravelRecordTag(travelRecord, tag);
    }

    public Long getTravelRecordId() {
        return travelRecord.getId();
    }

    public Tag getTag() {
        return tag;
    }
}
