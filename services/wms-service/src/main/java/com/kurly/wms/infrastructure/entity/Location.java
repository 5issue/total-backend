package com.kurly.wms.infrastructure.entity;

import com.kurly.wms.domain.enums.StorageType;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 보관존(파레트 랙), 피킹존(선반 빈), 버퍼(임시 도크)를 하나의 테이블에서 locationType으로 구분한다. */
@Entity
@Table(name = "location", uniqueConstraints = @UniqueConstraint(
        name = "uk_location_address",
        columnNames = {"warehouse_id", "aisle", "rack", "level", "bin"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", length = 20, nullable = false)
    private LocationType locationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20, nullable = false)
    private StorageType storageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone", length = 20, nullable = false)
    private Zone zone;

    @Column(name = "aisle", length = 20, nullable = false)
    private String aisle;

    @Column(name = "rack", length = 20, nullable = false)
    private String rack;

    @Column(name = "level", nullable = false)
    private Integer level;

    @Column(name = "bin", length = 20, nullable = false)
    private String bin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private LocationStatus status;

    @Builder
    private Location(Warehouse warehouse, LocationType locationType, StorageType storageType, Zone zone,
                      String aisle, String rack, Integer level, String bin, LocationStatus status) {
        this.warehouse = warehouse;
        this.locationType = locationType;
        this.storageType = storageType;
        this.zone = zone;
        this.aisle = aisle;
        this.rack = rack;
        this.level = level;
        this.bin = bin;
        this.status = status;
    }

    public void lock() {
        this.status = LocationStatus.LOCKED;
    }

    public void activate() {
        this.status = LocationStatus.ACTIVE;
    }

    public enum LocationType {
        PALLET_RACK,
        SHELF_BIN,
        BUFFER
    }

    public enum LocationStatus {
        ACTIVE,
        LOCKED
    }

    /**
     * 로케이션의 기능적 구역. locationType(PALLET_RACK/SHELF_BIN/BUFFER)과 지금은 사실상
     * 1:1로 겹친다(STORAGE=PALLET_RACK, PICKING=SHELF_BIN, BUFFER=BUFFER) — 나중에 한
     * 구역(zone) 안에 여러 locationType이 섞이는 시점부터 별도 컬럼의 의미가 생긴다.
     */
    public enum Zone {
        BUFFER,
        PICKING,
        STORAGE
    }
}
