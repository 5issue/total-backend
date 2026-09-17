package com.kurly.wms.infrastructure.entity;

import com.kurly.common.exception.BusinessException;
import com.kurly.wms.domain.exception.WmsErrorCode;
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
import jakarta.persistence.Version;
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

    /** 검수/적치처럼 같은 행을 읽어 상태를 전이시키는 동시 요청을 막기 위한 낙관적 락. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    /**
     * 검수 완료 처리. totalBaseQuantity는 상품의 박스/파레트 환산 계수를 적용해 호출부에서 계산해
     * 전달한다. targetLocation은 아직 로케이션 배정 전략이 정해지지 않아(docs/erd-spec.md 미결
     * 사항 4번) null일 수 있다 — put-away를 구현하는 시점에 채워진다.
     */
    public void inspect(int inspectQuantity, int totalBaseQuantity, String lotNo, LocalDate expiredDate, Location targetLocation) {
        if(this.getStatus() != InboundItemStatus.PENDING) {
            throw new BusinessException(WmsErrorCode.INVALID_INBOUND_ITEM_STATUS,
                    "검수 가능한 상태가 아닙니다. inboundItemId=" + this.getId() + ", status=" + this.getStatus());
        }
        this.inspectQuantity = inspectQuantity;
        this.totalBaseQuantity = totalBaseQuantity;
        this.lotNo = lotNo;
        this.expiredDate = expiredDate;
        this.targetLocation = targetLocation;
        this.status = InboundItemStatus.INSPECTED;
    }

    /** 아직 물리 이동(put-away)이 끝나지 않은 상태에서 추천 로케이션을 다시 배정할 때 사용한다. */
    public void reassignTargetLocation(Location targetLocation) {
        this.targetLocation = targetLocation;
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
