package com.mapmory.backend.region.domain;

import com.mapmory.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "region")
public class Region extends BaseEntity {

    public static final String KOREA = "KR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Region parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_id")
    private Region root;

    @Column(name = "region_code", nullable = false, length = 20)
    private String regionCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "region_type", nullable = false, length = 20)
    private RegionType regionType;

    protected Region() {
    }

    private Region(Region parent, Region root, String regionCode, String name, RegionType regionType) {
        this.parent = parent;
        this.root = root;
        this.regionCode = regionCode;
        this.name = name;
        this.regionType = regionType;
    }

    public static Region of(Region parent, Region root, String regionCode, String name, RegionType regionType) {
        return new Region(parent, root, regionCode, name, regionType);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Region getParent() {
        return parent;
    }

    public Region getRoot() {
        return root;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public RegionType getRegionType() {
        return regionType;
    }

    public boolean isCountry(String countryCode) {
        return regionType == RegionType.COUNTRY && regionCode.equals(countryCode);
    }

    /** 이 지역이 속한 국가. 국가면 자기 자신이다. */
    public Region country() {
        return regionType == RegionType.COUNTRY ? this : root;
    }

    /** 이 지역이 속한 시·도. 국가면 null이다. */
    public Region province() {
        return switch (regionType) {
            case COUNTRY -> null;
            case PROVINCE -> this;
            case DISTRICT -> parent;
        };
    }

    /** 시·군·구면 자기 자신이고, 아니면 null이다. */
    public Region district() {
        return regionType == RegionType.DISTRICT ? this : null;
    }
}
