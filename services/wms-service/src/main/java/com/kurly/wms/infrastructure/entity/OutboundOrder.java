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
    @Column(name = "status", length = 20, nullable = false)
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

    public enum OutboundOrderStatus {
        ALLOCATED,
        PICKING,
        PACKING,
        COMPLETED,
        CANCELED
    }
}
