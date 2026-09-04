package com.kurly.order.domain.order;

import com.kurly.order.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String orderNo;

    @Column(nullable = false)
    private Long memberId;

    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false)
    private Long itemAmount;

    @Column(nullable = false)
    private Long shippingFee;

    @Column(nullable = false)
    private Long paymentAmount;

    private LocalDateTime paidAt;

    private Long omsOrderId;

    @Column(length = 30)
    private String fulfillmentStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DeliveryStatus deliveryStatus;

    private LocalDateTime expectedDeliveryAt;

    @Column(length = 64)
    private String inventoryReservationToken;

    private LocalDateTime inventoryReservedUntil;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Builder(access = AccessLevel.PRIVATE)
    private Order(String orderNo, Long memberId, OrderStatus status, Long itemAmount,
                  Long shippingFee, Long paymentAmount, String inventoryReservationToken,
                  LocalDateTime inventoryReservedUntil) {
        this.orderNo = orderNo;
        this.memberId = memberId;
        this.status = status;
        this.itemAmount = itemAmount;
        this.shippingFee = shippingFee;
        this.paymentAmount = paymentAmount;
        this.inventoryReservationToken = inventoryReservationToken;
        this.inventoryReservedUntil = inventoryReservedUntil;
    }

    public static Order createCheckout(
            String orderNo,
            Long memberId,
            String reservationToken,
            LocalDateTime reservedUntil,
            Long shippingFee,
            List<OrderItem> items
    ) {
        Assert.hasText(orderNo, "주문번호는 필수입니다.");
        Assert.notNull(memberId, "회원 ID는 필수입니다.");
        Assert.notEmpty(items, "주문 생성 시 주문 품목은 최소 1개 이상이어야 합니다.");

        long calculatedItemAmount = items.stream()
                .mapToLong(OrderItem::getLineAmount)
                .sum();
        long fee = shippingFee != null ? shippingFee : 0L;

        Order order = Order.builder()
                .orderNo(orderNo)
                .memberId(memberId)
                .status(OrderStatus.CHECKOUT_CREATED)
                .itemAmount(calculatedItemAmount)
                .shippingFee(fee)
                .paymentAmount(calculatedItemAmount + fee)
                .inventoryReservationToken(reservationToken)
                .inventoryReservedUntil(reservedUntil)
                .build();

        for (OrderItem item : items) {
            order.items.add(item);
            item.assignOrder(order);
        }

        return order;
    }

    public void markPaymentPending(LocalDateTime paymentExpiredUntil) {
        Assert.isTrue(this.status == OrderStatus.CHECKOUT_CREATED, "CHECKOUT_CREATED 상태에서만 결제 대기로 전이할 수 있습니다.");
        this.status = OrderStatus.PENDING_PAYMENT;
        this.inventoryReservedUntil = paymentExpiredUntil;
    }

    public void markPaid(Long paymentId, LocalDateTime paidAt) {
        Assert.isTrue(this.status == OrderStatus.PENDING_PAYMENT, "PENDING_PAYMENT 상태에서만 결제 완료 처리가 가능합니다.");
        this.status = OrderStatus.PAID;
        this.paymentId = paymentId;
        this.paidAt = paidAt;
        this.inventoryReservationToken = null;
    }

    public void markExpired() {
        this.status = OrderStatus.CANCELLED_EXPIRED;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }
}