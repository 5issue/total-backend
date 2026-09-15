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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** FEFO(유통기한 오름차순)로 할당된 LOT/로케이션에서 얼마를 피킹할지 지정하는 출고 상세. */
@Entity
@Table(name = "outbound_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_order_id", nullable = false)
    private OutboundOrder outboundOrder;

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
    @JoinColumn(name = "location_id")
    private Location location;

    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "picked_quantity", nullable = false)
    private Integer pickedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private OutboundItemStatus status;

    @Builder
    private OutboundItem(OutboundOrder outboundOrder, WmsProduct product, String lpnCode, String lotNo,
                          LocalDate expiredDate, Location location, Integer orderedQuantity) {
        this.outboundOrder = outboundOrder;
        this.product = product;
        this.lpnCode = lpnCode;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.location = location;
        this.orderedQuantity = orderedQuantity;
        this.pickedQuantity = 0;
        this.status = OutboundItemStatus.PENDING;
    }

    public void pick(int pickedQuantity) {
        this.pickedQuantity = pickedQuantity;
        this.status = (pickedQuantity < orderedQuantity) ? OutboundItemStatus.SHORTAGE : OutboundItemStatus.PICKED;
    }

    public enum OutboundItemStatus {
        PENDING,
        PICKED,
        SHORTAGE
    }
}
