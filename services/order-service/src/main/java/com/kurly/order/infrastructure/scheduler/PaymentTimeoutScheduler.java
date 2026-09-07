package com.kurly.order.infrastructure.scheduler;

import com.kurly.order.application.OrderService;
import com.kurly.order.domain.order.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentTimeoutScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.payment-timeout-scan-delay:10000}")
    public void expirePendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        orderRepository.findExpiredPaymentOrderIds(now).forEach(orderId -> orderService.expire(orderId, now));
    }
}
