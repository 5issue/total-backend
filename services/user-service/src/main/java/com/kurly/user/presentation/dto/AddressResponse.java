package com.kurly.user.presentation.dto;

import com.kurly.user.domain.entity.DeliveryAddress;

/** 배송지 목록 항목. */
public record AddressResponse(
        Long addressId,
        String addressName,
        String recipientName,
        String phone,
        String zipCode,
        String address,
        String addressDetail,
        boolean isDefault,
        String accessMethod
) {

    public static AddressResponse from(DeliveryAddress a) {
        return new AddressResponse(
                a.getId(),
                a.getAddressName(),
                a.getRecipientName(),
                a.getPhone(),
                a.getZipCode(),
                a.getAddress(),
                a.getAddressDetail(),
                a.isDefaultAddress(),
                a.getAccessMethod());
    }
}
