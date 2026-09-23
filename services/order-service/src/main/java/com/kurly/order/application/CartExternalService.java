package com.kurly.order.application;

import com.kurly.order.infrastructure.dto.AddressResponse;
import com.kurly.order.infrastructure.dto.CartProductInfo;
import com.kurly.order.infrastructure.dto.DeliveryAddressResponseDto;

import java.util.List;

public interface CartExternalService {
    AddressResponse getAddress(Long memberId, Long addressId);

    List<CartProductInfo> getProducts(List<Long> productIds);

    DeliveryAddressResponseDto.Promise getDeliveryPromise(AddressResponse address);
}
