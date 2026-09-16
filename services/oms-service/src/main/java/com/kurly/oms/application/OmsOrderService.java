package com.kurly.oms.application;

import com.kurly.oms.domain.order.OmsOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsOrderService {

    private final OmsOrderRepository omsOrderRepository;

    @Transactional(readOnly = true)
    public Object listOrders() {
        return null;
    }

    @Transactional(readOnly = true)
    public Object getOrderMonitoring(Long omsOrderId) {
        return null;
    }

    @Transactional
    public void cancelOrderByAdmin(Long orderId) {
    }

    @Transactional(readOnly = true)
    public boolean checkCancelEligibility(Long orderId) {
        return false;
    }
}