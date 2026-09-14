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

/** 현장 작업 단위(파레트/박스)로 검수하고 최종 낱개(EA)로 환산해 LOT/유통기한과 함께 기록하는 입고 상세. */
@Entity
@Table(name = "inbound_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_order_id", nullable = false)
    private InboundOrder inboundOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private WmsProduct product;

    @Enumerated(EnumType.STRING)
    @Column(name = "inbound_unit", length = 20, nullable = false)
    private InboundUnit inboundUnit;

    @Column(name = "ordered_quantity", nullable = false)
    private Integer orderedQuantity;

    @Column(name = "inspect_quantity")
    private Integer inspectQuantity;

    @Column(name = "total_base_quantity")
    private Integer totalBaseQuantity;

    @Column(name = "lpn_code", length = 50)
    private String lpnCode;

    @Column(name = "lot_no", length = 50)
    private String lotNo;

    @Column(name = "expired_date")
    private LocalDate expiredDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_location_id")
    private Location targetLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InboundItemStatus status;

    @Builder
    private InboundItem(InboundOrder inboundOrder, WmsProduct product, InboundUnit inboundUnit,
                         Integer orderedQuantity, String lpnCode, String lotNo, LocalDate expiredDate,
                         Location targetLocation) {
        this.inboundOrder = inboundOrder;
        this.product = product;
        this.inboundUnit = inboundUnit;
        this.orderedQuantity = orderedQuantity;
        this.lpnCode = lpnCode;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.targetLocation = targetLocation;
        this.status = InboundItemStatus.PENDING;
    }

    /** 검수 완료 처리. totalBaseQuantity는 상품의 박스/파레트 환산 계수를 적용해 호출부에서 계산해 전달한다. */
    public void inspect(int inspectQuantity, int totalBaseQuantity) {
        this.inspectQuantity = inspectQuantity;
        this.totalBaseQuantity = totalBaseQuantity;
        this.status = InboundItemStatus.INSPECTED;
    }

    public void putAway(Location targetLocation) {
        this.targetLocation = targetLocation;
        this.status = InboundItemStatus.PUT_AWAY;
    }

    public enum InboundUnit {
        PALLET,
        CARTON,
        BOX
    }

    public enum InboundItemStatus {
        PENDING,
        INSPECTED,
        PUT_AWAY
    }
}
