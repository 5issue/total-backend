package com.kurly.order.application;

import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.infrastructure.dto.AddressResponse;
import com.kurly.order.infrastructure.dto.CancelEligibilityResponse;

import java.util.List;
import java.util.UUID;

public interface OrderExternalService {

    void holdInventory(UUID reservationToken, List<CartItem> items);

    AddressResponse getAddress(Long memberId, Long addressId);

    void releaseInventory(UUID reservationToken);

    CancelEligibilityResponse getCancelEligibility(Long orderId);

    void cancelPayment(Long paymentId, String idempotencyKey, String cancelReason);

}
