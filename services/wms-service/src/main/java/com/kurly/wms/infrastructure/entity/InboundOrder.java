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

/** SCM 서비스로부터 전달받는 발주/입고 예정 전표. */
@Entity
@Table(name = "inbound_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @Column(name = "supplier_name")
    private String supplierName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InboundOrderStatus status;

    @Column(name = "expected_date")
    private LocalDateTime expectedDate;

    @Column(name = "completed_date")
    private LocalDateTime completedDate;

    @Builder
    private InboundOrder(Warehouse warehouse, String supplierName, LocalDateTime expectedDate) {
        this.warehouse = warehouse;
        this.supplierName = supplierName;
        this.expectedDate = expectedDate;
        this.status = InboundOrderStatus.EXPECTED;
    }

    public void startInspection() {
        this.status = InboundOrderStatus.INSPECTING;
    }

    public void complete() {
        this.status = InboundOrderStatus.COMPLETED;
        this.completedDate = LocalDateTime.now();
    }

    public void cancel() {
        this.status = InboundOrderStatus.CANCELED;
    }

    public enum InboundOrderStatus {
        EXPECTED,
        INSPECTING,
        COMPLETED,
        CANCELED
    }
}
