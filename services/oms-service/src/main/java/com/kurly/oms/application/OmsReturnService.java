package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.infrastructure.messaging.OrderReturnRequestedMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OmsReturnService {

    private final OmsOrderRepository omsOrderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void receiveReturn(OrderReturnRequestedMessage event) {
        OmsOrder omsOrder = omsOrderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        omsOrder.markReturnRequested();

        log.info("[OmsReturnService] 반품 접수 완료 omsOrderId={}, orderId={}",
                omsOrder.getId(), event.orderId());
    }

    @Transactional
    public void approveColdChainReturn(Long returnId) {
    }

    @Transactional
    public void processLogisticsReturn(Long returnId) {
    }

}
