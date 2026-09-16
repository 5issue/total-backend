package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.common.BaseEntity;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "oms_return_processes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsReturnProcess extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long returnId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_order_id", nullable = false)
    private OmsOrder omsOrder;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, unique = true, length = 36)
    private String sourceEventId;

    @Column(nullable = false)
    private Long refundAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OmsReturnStatus status;

    public static OmsReturnProcess create(Long returnId, OmsOrder omsOrder, Long userId,
                                          Long paymentId, String sourceEventId, Long refundAmount) {
        Assert.notNull(returnId, "반품 ID는 필수입니다.");
        Assert.notNull(omsOrder, "OMS 주문은 필수입니다.");
        Assert.notNull(userId, "회원 ID는 필수입니다.");
        Assert.notNull(paymentId, "결제 ID는 필수입니다.");
        Assert.hasText(sourceEventId, "원본 이벤트 ID는 필수입니다.");
        Assert.notNull(refundAmount, "환불 예정 금액은 필수입니다.");

        OmsReturnProcess process = new OmsReturnProcess();
        process.returnId = returnId;
        process.omsOrder = omsOrder;
        process.userId = userId;
        process.paymentId = paymentId;
        process.sourceEventId = sourceEventId;
        process.refundAmount = refundAmount;
        process.status = OmsReturnStatus.REQUESTED;
        return process;
    }

    public boolean isColdChain() {
        return omsOrder.getItems().stream()
                .anyMatch(item -> item.getStorageType() == StorageType.CHILLED
                        || item.getStorageType() == StorageType.FROZEN);
    }

    public void approveColdChain() {
        Assert.state(isColdChain(), "콜드체인 반품이 아닙니다.");
        status = OmsReturnStatus.REFUND_APPROVED;
    }

    public void requestInspection() {
        Assert.state(!isColdChain(), "역물류 반품이 아닙니다.");
        status = OmsReturnStatus.INSPECTION_REQUESTED;
    }

    public void approveRefundAfterInspection() {
        Assert.state(status == OmsReturnStatus.INSPECTION_REQUESTED, "검수 요청 상태가 아닙니다.");
        status = OmsReturnStatus.REFUND_APPROVED;
    }
}
