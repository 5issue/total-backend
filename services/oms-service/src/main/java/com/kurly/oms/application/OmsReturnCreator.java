package com.kurly.oms.application;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.returnorder.OmsReturn;
import com.kurly.oms.domain.returnorder.OmsReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsReturnCreator {
    private final OmsReturnRepository repository;
    private final OmsOrderRepository orderRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OmsReturn create(Long orderId, String sourceEventId) {
        OmsOrder order = orderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));
        return repository.saveAndFlush(OmsReturn.createFromOrder(order, sourceEventId));
    }
}
