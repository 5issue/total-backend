package com.kurly.order.domain.order;

import com.kurly.order.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Builder
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
}
