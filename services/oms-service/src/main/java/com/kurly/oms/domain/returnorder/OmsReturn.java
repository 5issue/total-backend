package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.common.BaseEntity;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "oms_returns")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsReturn extends BaseEntity {

    @OneToMany(mappedBy = "omsReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OmsReturnItem> items = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long omsOrderId;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OmsReturnStatus status;

    @Column(length = 500)
    private String adminNote;

    @Column
    private Long totalRefundAmount;

    @Column
    private Long deductedShippingFee;

    private OmsReturn(Long omsOrderId, Long orderId) {
        this.omsOrderId = omsOrderId;
        this.orderId = orderId;
        this.status = OmsReturnStatus.REQUESTED;
    }

    public static OmsReturn createFromOrder(OmsOrder omsOrder) {
        Assert.notNull(omsOrder, "omsOrder는 필수입니다.");
        Assert.notEmpty(omsOrder.getItems(), "주문 품목이 비어있습니다.");

        OmsReturn omsReturn = new OmsReturn(omsOrder.getId(), omsOrder.getOrderId());

        for (OmsOrderItem orderItem : omsOrder.getItems()) {
            OmsReturnItem returnItem = OmsReturnItem.create(orderItem);
            omsReturn.items.add(returnItem);
            returnItem.assignReturn(omsReturn);
        }

        return omsReturn;
    }

    public void applyJudgement(String adminNote, boolean hasColdApproval, boolean hasLogisticsApproval) {
        this.adminNote = adminNote;

        if (hasLogisticsApproval) {
            this.status = OmsReturnStatus.PROCESSING;
        } else if (hasColdApproval) {
            this.status = OmsReturnStatus.COMPLETED;
        } else {
            this.status = OmsReturnStatus.REJECTED;
        }
    }

    public void completeInspection() {
        this.status = OmsReturnStatus.COMPLETED;
    }

    public void recordRefund(Long refundAmount, Long deductedFee) {
        this.totalRefundAmount = refundAmount;
        this.deductedShippingFee = deductedFee;
        this.status = OmsReturnStatus.COMPLETED;
    }

    public void recordInspectionFailure() {
        this.status = OmsReturnStatus.REJECTED;
        this.totalRefundAmount = 0L;
        this.deductedShippingFee = 0L;
    }
}