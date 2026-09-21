package com.kurly.oms.infrastructure.messaging;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderPaymentCompletedMessage(
        UUID eventId,
        Long orderId,
        String orderNo,
        Long memberId,
        Long regionId,
        Long paidAmount,
        LocalDateTime paidAt,
        DeliveryAddress deliveryAddress,
        List<Item> items
) {
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
            String storageType
    ) {
    }
}