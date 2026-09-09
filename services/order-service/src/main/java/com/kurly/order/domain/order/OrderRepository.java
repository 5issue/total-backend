package com.kurly.order.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdForUpdate(Long id);

    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    Optional<Order> findActiveCheckoutForUpdate(Long memberId);

    Page<Order> findOrders(Long memberId, LocalDateTime from, String productName, Pageable pageable);

    int completePayment(Long orderId, Long paymentId, LocalDateTime paidAt, LocalDateTime now);

    List<Long> findExpiredPaymentOrderIds(LocalDateTime now);

    int expirePayment(Long orderId, LocalDateTime now);
}
