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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/** OMS(주문 관리 시스템)로부터 전달받는 출고 지시 전표. */
@Entity
@Table(name = "outbound_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id")
    private Long orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private OutboundOrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private OutboundOrder(Long orderId, Warehouse warehouse) {
        this.orderId = orderId;
        this.warehouse = warehouse;
        this.status = OutboundOrderStatus.ALLOCATED;
    }

    /** 일부 품목이 FEFO 할당에 실패(피킹존 재고 부족)해 보충 지시 완료를 기다리는 상태로 전환한다. */
    public void hold() {
        this.status = OutboundOrderStatus.PENDING_REPLENISHMENT;
    }

    /** 대기 중이던 마지막 품목까지 재할당이 끝나(모든 OutboundItem이 ALLOCATED) 전표를 되돌린다. */
    public void allocate() {
        this.status = OutboundOrderStatus.ALLOCATED;
    }

    public void startPicking() {
        this.status = OutboundOrderStatus.PICKING;
    }

    public void startPacking() {
        this.status = OutboundOrderStatus.PACKING;
    }

    public void complete() {
        this.status = OutboundOrderStatus.COMPLETED;
    }

    public void cancel() {
        this.status = OutboundOrderStatus.CANCELED;
    }

    /** 포함된 품목 중 하나라도 장시간 재고를 확보하지 못해(UNALLOCATED) 전표 전체를 실패 처리한다. */
    public void fail() {
        this.status = OutboundOrderStatus.FAILED;
    }

    public enum OutboundOrderStatus {
        PENDING_REPLENISHMENT,
        ALLOCATED,
        PICKING,
        PACKING,
        COMPLETED,
        CANCELED,
        /** 품목 하나라도 장시간 재고를 확보하지 못해 최종 실패 처리됨(배치/스케줄러가 전환). */
        FAILED
    }
}
