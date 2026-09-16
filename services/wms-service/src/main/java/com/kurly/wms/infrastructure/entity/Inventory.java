package com.kurly.wms.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

/** 창고·로케이션·상품·LOT(유통기한)별로 분리된 실시간 가용/예약 재고. 상품 전체 가용 재고는 로케이션별 값을 합산해 조회한다. */
@Entity
@Table(name = "inventory", uniqueConstraints = @UniqueConstraint(
        name = "uk_inventory_lot",
        columnNames = {"warehouse_id", "location_id", "product_id", "lot_no", "expired_date", "lpn_code"}
))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private WmsProduct product;

    @Column(name = "lpn_code", length = 50)
    private String lpnCode;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "expired_date")
    private LocalDate expiredDate;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "reserved_quantity", nullable = false)
    private Integer reservedQuantity;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    private Inventory(Warehouse warehouse, Location location, WmsProduct product, String lpnCode, String lotNo,
                       LocalDate expiredDate, Integer quantity) {
        this.warehouse = warehouse;
        this.location = location;
        this.product = product;
        this.lpnCode = lpnCode;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.quantity = quantity;
        this.reservedQuantity = 0;
    }

    public int getAvailableQuantity() {
        return quantity - reservedQuantity;
    }

    /** 입고 검수 완료 등으로 실물 재고가 늘어날 때 사용한다. */
    public void receive(int amount) {
        this.quantity += amount;
    }

    /** put-away 등 로케이션 간 실물 이동으로 이 로케이션의 재고가 빠져나갈 때 사용한다. */
    public void remove(int amount) {
        this.quantity -= amount;
    }

    public void reserve(int amount) {
        this.reservedQuantity += amount;
    }

    public void release(int amount) {
        this.reservedQuantity = Math.max(0, this.reservedQuantity - amount);
    }
}
