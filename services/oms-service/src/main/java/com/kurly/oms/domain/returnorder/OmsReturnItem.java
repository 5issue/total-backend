package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.common.BaseEntity;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrderItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "oms_return_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsReturnItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_return_id", nullable = false)
    private OmsReturn omsReturn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oms_order_item_id", nullable = false)
    private OmsOrderItem orderItem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReturnDecision decision;

    @Column(length = 255)
    private String rejectReason;

    public static OmsReturnItem create(OmsOrderItem orderItem) {
        Assert.notNull(orderItem, "OmsOrderItem은 필수입니다.");
        OmsReturnItem returnItem = new OmsReturnItem();
        returnItem.orderItem = orderItem;
        returnItem.decision = ReturnDecision.PENDING;
        return returnItem;
    }

    void assignReturn(OmsReturn omsReturn) {
        this.omsReturn = omsReturn;
    }

    public void judge(ReturnDecision decision, String rejectReason) {
        this.decision = decision;
        if (decision == ReturnDecision.REJECT) {
            this.rejectReason = rejectReason;
        }
    }

    public Long getOmsOrderItemId() {
        return this.orderItem.getId();
    }

    public StorageType getOmsOrderStorageType() {
        return this.orderItem.getStorageType();
    }
}
