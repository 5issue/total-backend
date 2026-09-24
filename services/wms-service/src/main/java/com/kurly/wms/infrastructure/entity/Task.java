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
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 출고 피킹 전용 작업 지시. 보관존→피킹존 보충은 StockMovement가 이미 자체 상태·확정 API를
 * 갖고 있어 Task로 따로 감싸지 않는다(그러면 상태를 두 곳에서 따로 관리해야 해서). 그래서
 * outboundOrder/outboundItem은 필수다 — 정확히 어느 로케이션/LOT/수량을 피킹할지는 이
 * outboundItem을 보고 판단한다.
 *
 * <p>{@link TaskStatus}는 api-spec의 {@code WmsService.Common.TaskStatus}와 1:1로 대응한다.
 */
@Entity
@Table(name = "task")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_no", length = 64, nullable = false, unique = true)
    private String taskNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private TaskStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_order_id", nullable = false)
    private OutboundOrder outboundOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "outbound_item_id", nullable = false, unique = true)
    private OutboundItem outboundItem;

    /** 아직 Worker 엔티티/테이블이 없어 ID만 보관한다(erd-spec.md 미결 사항 참고). */
    @Column(name = "worker_id")
    private Long workerId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private Task(String taskNo, Warehouse warehouse, OutboundOrder outboundOrder, OutboundItem outboundItem) {
        this.taskNo = taskNo;
        this.warehouse = warehouse;
        this.outboundOrder = outboundOrder;
        this.outboundItem = outboundItem;
        this.status = TaskStatus.PENDING;
    }

    public enum TaskStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELED
    }
}
