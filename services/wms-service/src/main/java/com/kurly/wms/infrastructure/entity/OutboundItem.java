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
    @Column(name = "status", length = 30, nullable = false)
    private OutboundItemStatus status;

    /**
     * location(피킹존 로케이션)이 채워져 있으면 FEFO 할당이 끝난 것이므로 무조건 ALLOCATED다 —
     * 이 불변조건은 호출부가 깨뜨릴 수 없다. location이 비어 있으면 pendingStatus를 그대로
     * 쓴다 — 보충 지시(StockMovement)까지는 예약된 PENDING_REPLENISHMENT인지, 그마저도 못한
     * UNALLOCATED인지는 상황에 따라 다르므로 호출부가 명시해야 한다(location이 채워지는
     * ALLOCATED 케이스에서는 무시되므로 생략 가능).
     */
    @Builder
    private OutboundItem(OutboundOrder outboundOrder, WmsProduct product, String lpnCode, String lotNo,
                          LocalDate expiredDate, Location location, Integer orderedQuantity,
                          OutboundItemStatus pendingStatus) {
        this.outboundOrder = outboundOrder;
        this.product = product;
        this.lpnCode = lpnCode;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.location = location;
        this.orderedQuantity = orderedQuantity;
        this.pickedQuantity = 0;
        this.status = (location != null) ? OutboundItemStatus.ALLOCATED : pendingStatus;
    }

    public void pick(int pickedQuantity) {
        this.pickedQuantity = pickedQuantity;
        this.status = (pickedQuantity < orderedQuantity) ? OutboundItemStatus.SHORTAGE : OutboundItemStatus.PICKED;
    }

    /** 장시간 재고를 확보하지 못해(UNALLOCATED 등) 최종 실패 처리한다. */
    public void fail() {
        this.status = OutboundItemStatus.FAILED;
    }

    /**
     * PUT_AWAY로 보관존 재고가 늘어 재시도한 결과 보충 지시(StockMovement)를 걸 수 있게 됐다는
     * 뜻으로 전환한다. 위치는 그 이동이 실제로 완료되기 전까지 여전히 모르므로 그대로 null이다.
     */
    public void markReplenishmentReserved() {
        this.status = OutboundItemStatus.PENDING_REPLENISHMENT;
    }

    /** 재시도/재할당이 이 행의 수량 중 일부만 처리했을 때, 아직 처리되지 않은 나머지로 줄인다. */
    public void reduceOrderedQuantity(int amount) {
        this.orderedQuantity -= amount;
    }

    /** REPLENISHMENT 이동이 실제로 완료돼 이 행이 최종적으로 어디서 피킹될지 확정한다. */
    public void allocate(Location location, String lotNo, LocalDate expiredDate) {
        this.location = location;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.status = OutboundItemStatus.ALLOCATED;
    }

    public enum OutboundItemStatus {
        /** 피킹존은 물론 보관존에서도 예약조차 못한 상태. StockMovement가 전혀 없다. PUT_AWAY로
         *  보관존 재고가 늘어나면 재시도 대상이며, 장시간 지속되면 FAILED로 전환된다. */
        UNALLOCATED,
        /** 보관존 재고 예약 + 보충 지시(StockMovement, REPLENISHMENT)까지는 생성됨. 그 물리 이동
         *  완료만 기다리면 된다. location/lotNo/expiredDate는 아직 null이다. */
        PENDING_REPLENISHMENT,
        /** 피킹존 재고(location, lotNo)에 매핑되고 reserved_quantity 증가까지 끝난 상태. */
        ALLOCATED,
        PICKED,
        SHORTAGE,
        /** 장시간 UNALLOCATED으로 남아 있어 최종 실패 처리됨(배치/스케줄러가 전환). */
        FAILED
    }
}
