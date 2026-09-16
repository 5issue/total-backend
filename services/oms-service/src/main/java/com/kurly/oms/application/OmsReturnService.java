package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsReturnService {

    private final OmsReturnProcessRepository processRepository;
    private final OmsOrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Object listReturns() {
        return processRepository.findAll().stream().map(ReturnProcessView::from).toList();
    }

    @Transactional(readOnly = true)
    public Object getReturnDetail(Long returnId) {
        return ReturnProcessView.from(getProcess(returnId));
    }

    @Transactional
    public void approveColdChainReturn(Long returnId) {
        OmsReturnProcess process = getProcess(returnId);
        process.approveColdChain();
        eventPublisher.publishEvent(RefundRequestedEvent.from(process, "RETURN_APPROVED"));
    }

    @Transactional
    public void processLogisticsReturn(Long returnId) {
        OmsReturnProcess process = getProcess(returnId);
        process.requestInspection();
        eventPublisher.publishEvent(ReturnInspectionRequestedEvent.from(process));
    }

    @Transactional
    public void receiveInspectionResult(Long returnId) {
        OmsReturnProcess process = getProcess(returnId);
        process.approveRefundAfterInspection();
        eventPublisher.publishEvent(RefundRequestedEvent.from(process, "RETURN_INSPECTED"));
    }

    @Transactional
    public void receive(ReturnRequestedCommand command) {
        if (processRepository.existsBySourceEventId(command.eventId())) {
            return;
        }
        OmsOrder omsOrder = orderRepository.findByOrderId(command.orderId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));
        processRepository.save(OmsReturnProcess.create(command.returnId(), omsOrder, command.userId(),
                command.paymentId(), command.eventId(), command.expectedRefundAmount()));
    }

    private OmsReturnProcess getProcess(Long returnId) {
        return processRepository.findByReturnId(returnId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_RETURN_NOT_FOUND));
    }

    public record ReturnRequestedCommand(String eventId, Long returnId, Long orderId, Long userId,
                                         Long paymentId, String reason, String reasonDetail,
                                         Long expectedRefundAmount) {
    }

    public record ReturnProcessView(Long returnId, Long orderId, Long paymentId,
                                    OmsReturnStatus status, boolean coldChain) {
        static ReturnProcessView from(OmsReturnProcess process) {
            return new ReturnProcessView(process.getReturnId(), process.getOmsOrder().getOrderId(),
                    process.getPaymentId(), process.getStatus(), process.isColdChain());
        }
    }
}
