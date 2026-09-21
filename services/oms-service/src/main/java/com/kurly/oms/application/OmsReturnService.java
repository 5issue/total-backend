package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.*;
import com.kurly.oms.infrastructure.messaging.OmsRefundRequestedEvent;
import com.kurly.oms.infrastructure.messaging.OmsReturnInspectionRequestedEvent;
import com.kurly.oms.infrastructure.messaging.OrderReturnRequestedMessage;
import com.kurly.oms.infrastructure.messaging.WmsReturnInspectedMessage;
import com.kurly.oms.presentation.dto.ReturnJudgementRequest;
import com.kurly.oms.presentation.dto.ReturnJudgementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OmsReturnService {

    private final OmsOrderRepository omsOrderRepository;
    private final OmsReturnRepository omsReturnRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void receiveReturn(OrderReturnRequestedMessage event) {
        OmsOrder omsOrder = omsOrderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));

        if (omsReturnRepository.existsByOmsOrderId(omsOrder.getId())) {
            log.warn("[OmsReturnService] 이미 반품 접수된 주문입니다. omsOrderId={}", omsOrder.getId());
            return;
        }

        OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrder);
        omsReturnRepository.save(omsReturn);

        log.info("[OmsReturnService] OmsReturn 생성 완료 omsOrderId={}, returnId={}",
                omsOrder.getId(), omsReturn.getId());
    }

    @Transactional
    public ReturnJudgementResponse judgeReturn(Long omsOrderId, ReturnJudgementRequest request) {
        OmsReturn omsReturn = omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_RETURN_NOT_FOUND));

        Map<Long, OmsReturnItem> returnItemMap = omsReturn.getItems().stream()
                .collect(Collectors.toMap(OmsReturnItem::getOmsOrderItemId, item -> item));

        List<Long> coldApprovedItemIds = new ArrayList<>();
        List<Long> logisticsApprovedItemIds = new ArrayList<>();
        List<Long> rejectedItemIds = new ArrayList<>();

        for (ReturnJudgementRequest.ItemJudgement judgement : request.judgements()) {
            OmsReturnItem item = returnItemMap.get(judgement.omsOrderItemId());

            if (item == null) {
                throw new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND, "주문에 속하지 않은 품목 ID: " + judgement.omsOrderItemId());
            }

            if (judgement.decision() == ReturnDecision.APPROVE_COLDCHAIN && item.getOmsOrderStorageType() == StorageType.ROOM) {
                throw new BusinessException(OmsErrorCode.OMS_CONFLICT_TEMPERATURE);
            }

            item.judge(judgement.decision(), judgement.rejectReason());

            switch (judgement.decision()) {
                case APPROVE_COLDCHAIN -> coldApprovedItemIds.add(item.getOmsOrderItemId());
                case APPROVE_LOGISTICS -> logisticsApprovedItemIds.add(item.getOmsOrderItemId());
                case REJECT -> rejectedItemIds.add(item.getOmsOrderItemId());
            }
        }

        omsReturn.applyJudgement(
                request.adminNote(),
                !coldApprovedItemIds.isEmpty(),
                !logisticsApprovedItemIds.isEmpty()
        );

        if (!coldApprovedItemIds.isEmpty()) {
            long coldRefundAmount = omsReturn.getItems().stream()
                    .filter(item -> coldApprovedItemIds.contains(item.getOmsOrderItemId()))
                    .mapToLong(item -> item.getOrderItem().getUnitPrice() * item.getOrderItem().getQuantity())
                    .sum();

            eventPublisher.publishEvent(OmsRefundRequestedEvent.of(
                    omsReturn.getOmsOrderId(),
                    omsReturn.getOrderId(),
                    coldRefundAmount,
                    0L,
                    coldApprovedItemIds
            ));
        }

        if (!logisticsApprovedItemIds.isEmpty()) {
            eventPublisher.publishEvent(OmsReturnInspectionRequestedEvent.of(
                    omsReturn.getOmsOrderId(), omsReturn.getOrderId(), logisticsApprovedItemIds));
        }

        return new ReturnJudgementResponse(
                omsOrderId,
                omsReturn.getStatus(),
                coldApprovedItemIds,
                logisticsApprovedItemIds,
                rejectedItemIds,
                LocalDateTime.now()
        );
    }

    @Transactional
    public void processInspectionResult(WmsReturnInspectedMessage message) {
        OmsReturn omsReturn = omsReturnRepository.findByIdWithDetails(message.omsReturnId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_RETURN_NOT_FOUND));

        if (omsReturn.getStatus() != OmsReturnStatus.PROCESSING) {
            throw new BusinessException(OmsErrorCode.OMS_INVALID_STATUS);
        }

        if ("FAILED".equalsIgnoreCase(message.inspectionResult()) || message.approvedItemIds().isEmpty()) {
            log.warn("[OmsReturnService] 검수 전량 불합격 처리: returnId={}, note={}", message.omsReturnId(), message.wmsNote());
            omsReturn.recordInspectionFailure();
            return;
        }

        long approvedItemsTotalAmount = omsReturn.getItems().stream()
                .filter(item -> message.approvedItemIds().contains(item.getOmsOrderItemId()))
                .mapToLong(item -> item.getOrderItem().getUnitPrice() * item.getOrderItem().getQuantity())
                .sum();

        long deductedFee = "CUSTOMER".equalsIgnoreCase(message.faultType()) ? 3000L : 0L;
        long finalRefundAmount = Math.max(0L, approvedItemsTotalAmount - deductedFee);

        omsReturn.recordRefund(finalRefundAmount, deductedFee);

        eventPublisher.publishEvent(OmsRefundRequestedEvent.of(
                omsReturn.getOmsOrderId(),
                omsReturn.getOrderId(),
                finalRefundAmount,
                deductedFee,
                message.approvedItemIds()
        ));

        log.info("[OmsReturnService] WMS 검수 기반 환불 요청 이벤트 발행 완료: returnId={}, finalRefundAmount={}, deductedFee={}",
                omsReturn.getId(), finalRefundAmount, deductedFee);
    }
}