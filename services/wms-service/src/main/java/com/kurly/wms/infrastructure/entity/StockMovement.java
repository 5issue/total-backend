package com.kurly.wms.infrastructure.entity;

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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** 입고 후 적치, 피킹존 보충, 단순 로케이션 이동 등 창고 내 물리적 이동을 관리하는 작업 지시. */
@Entity
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private WmsProduct product;

    @Column(name = "lpn_code", length = 50)
    private String lpnCode;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "expired_date")
    private LocalDate expiredDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_location_id")
    private Location fromLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id")
    private Location toLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_unit", length = 20, nullable = false)
    private MovementUnit movementUnit;

    @Column(name = "unit_quantity", nullable = false)
    private Integer unitQuantity;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", length = 20, nullable = false)
    private MovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private MovementStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Builder
    private StockMovement(Warehouse warehouse, WmsProduct product, String lpnCode, String lotNo,
                           LocalDate expiredDate, Location fromLocation, Location toLocation,
                           MovementUnit movementUnit, Integer unitQuantity, Integer quantity,
                           MovementType movementType) {
        this.warehouse = warehouse;
        this.product = product;
        this.lpnCode = lpnCode;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.movementUnit = movementUnit;
        this.unitQuantity = unitQuantity;
        this.quantity = quantity;
        this.movementType = movementType;
        this.status = MovementStatus.PENDING;
    }

    public void start() {
        this.status = MovementStatus.IN_PROGRESS;
    }

    public void complete() {
        this.status = MovementStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = MovementStatus.CANCELED;
    }

    public enum MovementUnit {
        PALLET,
        BOX,
        EA
    }

    public enum MovementType {
        PUT_AWAY,
        REPLENISHMENT,
        RELOCATION
    }

    public enum MovementStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELED
    }
}
