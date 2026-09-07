package com.kurly.order.application;

import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.presentation.dto.CheckoutInventoryResponseDto;

import java.util.List;

public interface OrderExternalService {

    CheckoutInventoryResponseDto holdInventory(List<CartItem> items);

    void releaseInventory(String reservationToken);

    boolean isCancellationEligible(Long orderId);

    void cancelPayment(Long paymentId);
}
