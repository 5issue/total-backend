package com.kurly.order.domain.order;

import com.kurly.order.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Builder
@Entity
@Table(name = "order_delivery_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OrderDeliveryInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    private Long sourceAddressId;

    @Column(nullable = false, length = 80)
    private String recipientName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 10)
    private String zipCode;

    @Column(nullable = false)
    private String address;

    private String addressDetail;

    @Column(length = 50)
    private String addressName;

    @Column(length = 30)
    private String accessMethod;

    private String accessDetail;

    @Column(length = 30)
    private String packingType;

    private String deliveryMessage;
}

