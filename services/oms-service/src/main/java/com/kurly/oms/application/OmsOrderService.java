package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.presentation.dto.CancelEligibilityResponseDto;
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
    public CancelEligibilityResponseDto checkCancelEligibility(Long orderId) {
        OmsOrder omsOrder = omsOrderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        return CancelEligibilityResponseDto.from(omsOrder);
    }
}