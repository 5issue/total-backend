package com.kurly.order.infrastructure.messaging;

import com.kurly.order.domain.common.StorageType;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderDeliveryInfo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderPaymentCompletedEvent(
        UUID eventId,
        Long orderId,
        String orderNo,
        Long memberId,
        Long paymentId,
        Long regionId,
        Long paidAmount,
        LocalDateTime paidAt,
        DeliveryAddress deliveryAddress,
        List<Item> items,
        LocalDateTime occurredAt
) {
    public static OrderPaymentCompletedEvent of(Order order, OrderDeliveryInfo deliveryInfo) {
        return new OrderPaymentCompletedEvent(
                UUID.randomUUID(),
                order.getId(),
                order.getOrderNo(),
                order.getMemberId(),
                deliveryInfo.getRegionId(),
                order.getPaymentId(),
                order.getPaymentAmount(),
                order.getPaidAt(),
                new DeliveryAddress(
                        deliveryInfo.getRecipientName(),
                        deliveryInfo.getPhone(),
                        deliveryInfo.getZipCode(),
                        deliveryInfo.getAddress(),
                        deliveryInfo.getAddressDetail()
                ),
                order.getItems().stream()
                        .map(item -> new Item(
                                item.getId(),
                                item.getProductId(),
                                item.getSkuId(),
                                item.getQuantity(),
                                item.getUnitPrice(),
                                item.getStorageType()
                        ))
                        .toList(),
                LocalDateTime.now()
        );
    }

    public record DeliveryAddress(
            String recipientName,
            String phone,
            String zipCode,
            String address,
            String addressDetail
    ) {
    }

    public record Item(
            Long orderItemId,
            Long productId,
            Long skuId,
            Integer quantity,
            Long unitPrice,
            StorageType storageType
    ) {
    }
}