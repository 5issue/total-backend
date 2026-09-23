package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.*;
import com.kurly.oms.infrastructure.messaging.*;
import com.kurly.oms.presentation.dto.ReturnJudgementRequest;
import com.kurly.oms.presentation.dto.ReturnJudgementResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OmsReturnService {

    private final static Long RETURN_SHIPPING_FEE = 3000L;
    private final OmsOrderRepository omsOrderRepository;
    private final OmsReturnRepository omsReturnRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final OmsReturnCreator returnCreator;

    public void receiveReturn(OrderReturnRequestedMessage event) {
        String sourceEventId = event.eventId().toString();
        if (omsReturnRepository.existsBySourceEventId(sourceEventId)) {
            log.info("[OmsReturnService] 이미 처리된 반품 이벤트 sourceEventId={}", sourceEventId);
            return;
        }
        OmsOrder omsOrder = omsOrderRepository.findByOrderId(event.orderId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND));
        if (omsReturnRepository.existsByOmsOrderId(omsOrder.getId())) {
            log.info("[OmsReturnService] 이미 반품 접수된 주문입니다. omsOrderId={}", omsOrder.getId());
            return;
        }
        try {
            OmsReturn omsReturn = returnCreator.create(event.orderId(), sourceEventId);
            log.info("[OmsReturnService] OmsReturn 생성 완료 omsOrderId={}, returnId={}", omsOrder.getId(), omsReturn.getId());
        } catch (DataIntegrityViolationException duplicate) {
            if (!omsReturnRepository.existsBySourceEventId(sourceEventId)
                    && !omsReturnRepository.existsByOmsOrderId(omsOrder.getId())) {
                throw duplicate;
            }
            log.info("[OmsReturnService] 동시 반품 접수 중복 omsOrderId={}", omsOrder.getId());
        }
    }

    @Transactional
    public ReturnJudgementResponse judgeReturn(Long omsOrderId, ReturnJudgementRequest request) {
        OmsReturn omsReturn = omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_RETURN_NOT_FOUND));

        Map<Long, OmsReturnItem> returnItemMap = omsReturn.getItems().stream()
                .collect(Collectors.toMap(OmsReturnItem::getOmsOrderItemId, item -> item));

        Set<Long> judgedIds = new HashSet<>();
        for (ReturnJudgementRequest.ItemJudgement judgement : request.judgements()) {
            if (!judgedIds.add(judgement.omsOrderItemId())) {
                throw new BusinessException(OmsErrorCode.OMS_INVALID_STATUS, "중복 판정 품목 ID: " + judgement.omsOrderItemId());
            }
        }

        List<Long> coldApprovedItemIds = new ArrayList<>();
        List<Long> logisticsApprovedItemIds = new ArrayList<>();
        List<Long> rejectedItemIds = new ArrayList<>();

        for (ReturnJudgementRequest.ItemJudgement judgement : request.judgements()) {
            OmsReturnItem item = returnItemMap.get(judgement.omsOrderItemId());

            if (item == null) {
                throw new BusinessException(OmsErrorCode.OMS_ORDER_NOT_FOUND, "주문에 속하지 않은 품목 ID: " + judgement.omsOrderItemId());
            }

            if (judgement.decision() == ReturnDecision.APPROVE_COLDCHAIN && item.getOmsOrderStorageType() == StorageType.ROOM_TEMPERATURE) {
                throw new BusinessException(OmsErrorCode.OMS_CONFLICT_TEMPERATURE);
            }

            item.judge(judgement.decision(), judgement.rejectReason());

            switch (judgement.decision()) {
                case APPROVE_COLDCHAIN -> coldApprovedItemIds.add(item.getOmsOrderItemId());
                case APPROVE_LOGISTICS -> logisticsApprovedItemIds.add(item.getOmsOrderItemId());
                case REJECT -> rejectedItemIds.add(item.getOmsOrderItemId());
            }
        }

        boolean hasCold = !coldApprovedItemIds.isEmpty();
        boolean hasLogistics = !logisticsApprovedItemIds.isEmpty();

        omsReturn.applyJudgement(request.adminNote(), hasCold, hasLogistics);

        // 1. 상온 항목이 있으면 WMS 수거 지시만 발행 (환불 이벤트는 WMS 검수 완료 시까지 지연)
        if (hasLogistics) {
            eventPublisher.publishEvent(OmsReturnInspectionRequestedEvent.of(
                    omsReturn.getOmsOrderId(), omsReturn.getOrderId(), logisticsApprovedItemIds));
        }
        // 2. 상온 항목 없이 순수 콜드체인(자체폐기) 승인건만 있는 경우 -> 즉시 단일 환불 이벤트 발행
        else if (hasCold) {
            long coldRefundAmount = omsReturn.getItems().stream()
                    .filter(item -> coldApprovedItemIds.contains(item.getOmsOrderItemId()))
                    .mapToLong(item -> item.getOrderItem().getUnitPrice() * item.getOrderItem().getQuantity())
                    .sum();

            omsReturn.recordRefund(coldRefundAmount, 0L);

            eventPublisher.publishEvent(OmsRefundRequestedEvent.of(
                    omsReturn.getOmsOrderId(),
                    omsReturn.getOrderId(),
                    coldRefundAmount,
                    0L,
                    coldApprovedItemIds
            ));
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

        Set<Long> eligibleIds = omsReturn.getItems().stream()
                .filter(item -> item.getDecision() == ReturnDecision.APPROVE_LOGISTICS)
                .map(OmsReturnItem::getOmsOrderItemId).collect(Collectors.toSet());
        Set<Long> approvedIds = new HashSet<>(message.approvedItemIds());
        if (approvedIds.size() != message.approvedItemIds().size() || !eligibleIds.containsAll(approvedIds)) {
            log.warn("[OmsReturnService] 반품 대상이 아닌 WMS 승인 품목: returnId={}, approvedIds={}", message.omsReturnId(), approvedIds);
            throw new BusinessException(OmsErrorCode.OMS_INVALID_STATUS);
        }

        if (approvedIds.isEmpty()) {
            log.warn("[OmsReturnService] 검수 전량 불합격 처리: returnId={}, note={}", message.omsReturnId(), message.wmsNote());

            // 기존 APPROVE_COLDCHAIN 승인된 건만 발행
            long coldOnlyAmount = omsReturn.getItems().stream()
                    .filter(item -> item.getDecision() == ReturnDecision.APPROVE_COLDCHAIN)
                    .mapToLong(item -> item.getOrderItem().getUnitPrice() * item.getOrderItem().getQuantity())
                    .sum();

            if (coldOnlyAmount > 0) {
                omsReturn.recordRefund(coldOnlyAmount, 0L);
                List<Long> coldItemIds = omsReturn.getItems().stream()
                        .filter(item -> item.getDecision() == ReturnDecision.APPROVE_COLDCHAIN)
                        .map(OmsReturnItem::getOmsOrderItemId).toList();

                eventPublisher.publishEvent(OmsRefundRequestedEvent.of(
                        omsReturn.getOmsOrderId(), omsReturn.getOrderId(), coldOnlyAmount, 0L, coldItemIds));
            } else {
                omsReturn.recordInspectionFailure();
            }
            return;
        }

        // 통합 환불 대상 = 기존 APPROVE_COLDCHAIN 품목 금액 + WMS 검수 합격(APPROVE_LOGISTICS) 품목 금액
        List<OmsReturnItem> allApprovedItems = omsReturn.getItems().stream()
                .filter(item -> item.getDecision() == ReturnDecision.APPROVE_COLDCHAIN
                                || (item.getDecision() == ReturnDecision.APPROVE_LOGISTICS && approvedIds.contains(item.getOmsOrderItemId())))
                .toList();

        long totalApprovedAmount = allApprovedItems.stream()
                .mapToLong(item -> item.getOrderItem().getUnitPrice() * item.getOrderItem().getQuantity())
                .sum();

        // 귀책 사유에 따른 배송비 차감 (고객 변심 시 3,000원)
        long deductedFee = ReturnFaultType.from(message.faultType()) == ReturnFaultType.CUSTOMER ? RETURN_SHIPPING_FEE : 0L;
        long finalRefundAmount = Math.max(0L, totalApprovedAmount - deductedFee);

        omsReturn.recordRefund(finalRefundAmount, deductedFee);

        List<Long> finalApprovedItemIds = allApprovedItems.stream()
                .map(OmsReturnItem::getOmsOrderItemId)
                .toList();

        eventPublisher.publishEvent(OmsRefundRequestedEvent.of(
                omsReturn.getOmsOrderId(),
                omsReturn.getOrderId(),
                finalRefundAmount,
                deductedFee,
                finalApprovedItemIds
        ));

        log.info("[OmsReturnService] 통합 환불 요청 이벤트 발행 완료: returnId={}, finalRefundAmount={}, deductedFee={}",
                omsReturn.getId(), finalRefundAmount, deductedFee);
    }

    @Transactional
    public void completeRefund(PaymentRefundCompletedMessage message) {
        OmsReturn omsReturn = omsReturnRepository.findByIdWithDetails(message.omsReturnId())
                .orElseThrow(() -> new BusinessException(OmsErrorCode.OMS_RETURN_NOT_FOUND));

        if (!java.util.Objects.equals(omsReturn.getTotalRefundAmount(), message.refundAmount())) {
            throw new IllegalStateException("환불 금액이 일치하지 않습니다.");
        }
        if (omsReturn.getStatus() == OmsReturnStatus.COMPLETED) {
            return;
        }
        if (omsReturn.getStatus() != OmsReturnStatus.REFUND_PENDING) {
            throw new BusinessException(OmsErrorCode.OMS_INVALID_STATUS);
        }
        omsReturn.completeRefund();
    }
}