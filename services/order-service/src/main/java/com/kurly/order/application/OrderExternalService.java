package com.kurly.order.application;

import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.presentation.dto.CheckoutInventoryResponseDto;
import com.kurly.order.presentation.dto.CartResponseDto;

import java.util.List;

public interface OrderExternalService {

    CheckoutInventoryResponseDto holdInventory(String reservationToken, List<CartItem> items);

    CartResponseDto.Address getAddress(Long memberId, Long addressId);

    void releaseInventory(String reservationToken);

    boolean isCancellationEligible(Long orderId);

    void cancelPayment(Long paymentId);

}
