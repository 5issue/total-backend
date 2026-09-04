package com.kurly.order.domain.order;

import com.kurly.order.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.Assert;

@Getter
@Entity
@Table(name = "order_delivery_info")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderDeliveryInfo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

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

    @Builder(access = AccessLevel.PRIVATE)
    private OrderDeliveryInfo(Order order, Long sourceAddressId, String recipientName, String phone,
                              String zipCode, String address, String addressDetail, String addressName,
                              String accessMethod, String accessDetail, String packingType, String deliveryMessage) {
        this.order = order;
        this.sourceAddressId = sourceAddressId;
        this.recipientName = recipientName;
        this.phone = phone;
        this.zipCode = zipCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.addressName = addressName;
        this.accessMethod = accessMethod;
        this.accessDetail = accessDetail;
        this.packingType = packingType;
        this.deliveryMessage = deliveryMessage;
    }

    public static OrderDeliveryInfo createSnapshot(
            Order order,
            Long sourceAddressId,
            String recipientName,
            String phone,
            String zipCode,
            String address,
            String addressDetail,
            String addressName,
            String accessMethod,
            String accessDetail,
            String packingType,
            String deliveryMessage
    ) {
        Assert.notNull(order, "연관 주문은 필수입니다.");
        Assert.hasText(recipientName, "수령인 이름은 필수입니다.");
        Assert.hasText(phone, "연락처는 필수입니다.");
        Assert.hasText(zipCode, "우편번호는 필수입니다.");
        Assert.hasText(address, "기본 주소는 필수입니다.");

        return OrderDeliveryInfo.builder()
                .order(order)
                .sourceAddressId(sourceAddressId)
                .recipientName(recipientName)
                .phone(phone)
                .zipCode(zipCode)
                .address(address)
                .addressDetail(addressDetail)
                .addressName(addressName)
                .accessMethod(accessMethod)
                .accessDetail(accessDetail)
                .packingType(packingType)
                .deliveryMessage(deliveryMessage)
                .build();
    }
}

