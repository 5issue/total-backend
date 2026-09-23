package com.kurly.oms.presentation.dto;

public record DeliveryPromiseRequest(
        Long addressId,
        String addressName,
        String recipientName,
        String recipientPhone,
        String address,
        String detailAddress
) {
}