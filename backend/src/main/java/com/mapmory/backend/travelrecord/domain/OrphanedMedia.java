package com.mapmory.backend.travelrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 기록에서 떨어져 나가 S3에서 지워야 할 객체. 행을 넣는 곳은 {@link OrphanedMediaRepository#enqueue}다.
 */
@Entity
@Table(name = "orphaned_media")
public class OrphanedMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "object_key", nullable = false, unique = true, length = 500)
    private String objectKey;

    protected OrphanedMedia() {
    }

    public Long getId() {
        return id;
    }

    public String getObjectKey() {
        return objectKey;
    }
}
