package com.kurly.oms.domain.order;

import com.kurly.oms.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
@Entity
@Table(name = "oms_orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OmsOrder extends BaseEntity {

    private static final Set<OmsOrderStatus> NON_CANCELLABLE_STATUSES = EnumSet.of(OmsOrderStatus.RELEASE_INSTRUCTED, OmsOrderStatus.FULFILLED, OmsOrderStatus.CANCELLED);

    private static final Set<OmsOrderStatus> RELEASE_REQUIRED_STATUSES = EnumSet.of(OmsOrderStatus.STOCK_REQUESTED, OmsOrderStatus.RELEASE_INSTRUCTED);

    @OneToMany(mappedBy = "omsOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OmsOrderItem> items = new ArrayList<>();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, length = 30)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OmsOrderStatus status;

    @Column(nullable = false, unique = true, length = 64)
    private String sourceEventId;

    @Column(nullable = false)
    private Long regionId;

    @Column(nullable = false, length = 80)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String recipientPhone;

    @Column(nullable = false, length = 10)
    private String postalCode;

    @Column(nullable = false)
    private String roadAddress;
    private String detailAddress;

    @Column(length = 40)
    private String cancelReasonCode;
    private LocalDateTime cancelledAt;

    @Builder(access = AccessLevel.PRIVATE)
    private OmsOrder(Long orderId, String orderNo, OmsOrderStatus status, String sourceEventId,
                     Long regionId, String recipientName, String recipientPhone,
                     String postalCode, String roadAddress, String detailAddress) {
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.status = status;
        this.sourceEventId = sourceEventId;
        this.regionId = regionId;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.postalCode = postalCode;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
    }

    public static OmsOrder create(Long orderId, String orderNo, String sourceEventId,
                                  Long regionId, String recipientName, String recipientPhone,
                                  String postalCode, String roadAddress, String detailAddress,
                                  List<OmsOrderItem> items) {
        Assert.notNull(orderId, "주문 서비스 orderId는 필수입니다.");
        Assert.hasText(orderNo, "주문번호는 필수입니다.");
        Assert.hasText(sourceEventId, "주문 이벤트 ID는 필수입니다.");
        Assert.notNull(regionId, "주문 서비스 regionId는 필수입니다.");
        Assert.hasText(recipientName, "수령인명은 필수입니다.");
        Assert.hasText(recipientPhone, "수령인 연락처는 필수입니다.");
        Assert.hasText(postalCode, "우편번호는 필수입니다.");
        Assert.hasText(roadAddress, "도로명 주소는 필수입니다.");
        Assert.notEmpty(items, "주문 품목은 최소 1개 이상이어야 합니다.");

        OmsOrder omsOrder = OmsOrder.builder()
                .orderId(orderId)
                .orderNo(orderNo)
                .status(OmsOrderStatus.ORDER_RECEIVED)
                .sourceEventId(sourceEventId)
                .regionId(regionId)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .postalCode(postalCode)
                .roadAddress(roadAddress)
                .detailAddress(detailAddress)
                .build();

        for (OmsOrderItem item : items) {
            omsOrder.items.add(item);
            item.assignOmsOrder(omsOrder);
        }

        return omsOrder;
    }

    public boolean isCancelableOrder() {
        return !NON_CANCELLABLE_STATUSES.contains(this.status);
    }

    public boolean isReleaseRequired() {
        return RELEASE_REQUIRED_STATUSES.contains(this.status);
    }

}