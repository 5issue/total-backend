package com.kurly.order.application;

import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.DeliveryAddressResponseDto;
import java.util.List;

public interface CartExternalService {
    CartResponseDto.Address getAddress(Long memberId, Long addressId);
    List<CartResponseDto.Product> getProducts(List<Long> productIds);
    DeliveryAddressResponseDto.Promise getDeliveryPromise(CartResponseDto.Address address);
}
