package com.kurly.user.presentation.dto;

public record DefaultAddressUpdateResponse(Long addressId, boolean isDefault) {

    public static DefaultAddressUpdateResponse of(Long addressId) {
        return new DefaultAddressUpdateResponse(addressId, true);
    }
}
