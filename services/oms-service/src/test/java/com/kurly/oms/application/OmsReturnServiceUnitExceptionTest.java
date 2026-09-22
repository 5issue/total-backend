// src/test/java/com/kurly/oms/application/OmsReturnServiceUnitExceptionTest.java

package com.kurly.oms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.*;
import com.kurly.oms.infrastructure.messaging.OrderReturnRequestedMessage;
import com.kurly.oms.infrastructure.messaging.PaymentRefundCompletedMessage;
import com.kurly.oms.infrastructure.messaging.WmsReturnInspectedMessage;
import com.kurly.oms.presentation.dto.ReturnJudgementRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OmsReturnServiceUnitExceptionTest {

    @Mock
    private OmsOrderRepository omsOrderRepository;

    @Mock
    private OmsReturnRepository omsReturnRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OmsReturnService omsReturnService;

    private OmsReturnItem createMockReturnItem(Long omsOrderItemId, StorageType storageType, long unitPrice, int quantity) {
        OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
        when(orderItemMock.getId()).thenReturn(omsOrderItemId);
        when(orderItemMock.getStorageType()).thenReturn(storageType);
        when(orderItemMock.getUnitPrice()).thenReturn(unitPrice);
        when(orderItemMock.getQuantity()).thenReturn(quantity);
        return OmsReturnItem.create(orderItemMock);
    }

    @Nested
    @DisplayName("반품 접수 예외 (receiveReturn)")
    class ReceiveReturnExceptionTest {

        @Test
        void 주문이_존재하지_않으면_OMS_ORDER_NOT_FOUND_예외가_발생한다() {
            // given
            OrderReturnRequestedMessage event = new OrderReturnRequestedMessage(
                    UUID.randomUUID(), 999L, 1L, LocalDateTime.now()
            );
            when(omsOrderRepository.findByOrderId(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsReturnService.receiveReturn(event))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_ORDER_NOT_FOUND);
        }
    }


    @Nested
    @DisplayName("반품 판정 예외 (judgeReturn)")
    class JudgeReturnExceptionTest {

        @Test
        void 반품이_존재하지_않으면_OMS_RETURN_NOT_FOUND_예외가_발생한다() {
            // given
            when(omsReturnRepository.findByOmsOrderIdWithDetails(anyLong())).thenReturn(Optional.empty());

            ReturnJudgementRequest request = new ReturnJudgementRequest(null, List.of());

            // when & then
            assertThatThrownBy(() -> omsReturnService.judgeReturn(999L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_RETURN_NOT_FOUND);
        }

        @Test
        void 주문에_속하지_않은_품목_ID로_판정하면_OMS_ORDER_NOT_FOUND_예외가_발생한다() {
            // given
            Long omsOrderId = 10L;

            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
            when(orderItemMock.getId()).thenReturn(1L);
            OmsReturnItem returnItem = OmsReturnItem.create(orderItemMock);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getItems()).thenReturn(List.of(returnItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    null,
                    List.of(new ReturnJudgementRequest.ItemJudgement(999L, ReturnDecision.REJECT, "사유"))
            );

            // when & then
            assertThatThrownBy(() -> omsReturnService.judgeReturn(omsOrderId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_ORDER_NOT_FOUND);
        }

        @Test
        void 상온_품목에_APPROVE_COLDCHAIN_판정하면_OMS_CONFLICT_TEMPERATURE_예외가_발생한다() {
            // given
            Long omsOrderId = 10L;

            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
            when(orderItemMock.getId()).thenReturn(1L);
            when(orderItemMock.getStorageType()).thenReturn(StorageType.ROOM);
            OmsReturnItem returnItem = OmsReturnItem.create(orderItemMock);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getItems()).thenReturn(List.of(returnItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    null,
                    List.of(new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.APPROVE_COLDCHAIN, null))
            );

            // when & then
            assertThatThrownBy(() -> omsReturnService.judgeReturn(omsOrderId, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_CONFLICT_TEMPERATURE);
        }
    }


    @Nested
    @DisplayName("검수 결과 처리 예외 (processInspectionResult)")
    class ProcessInspectionResultExceptionTest {

        @Test
        void 반품이_존재하지_않으면_OMS_RETURN_NOT_FOUND_예외가_발생한다() {
            // given
            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 999L, 10L, "PASSED", "CUSTOMER",
                    List.of(1L), List.of(), "메모", LocalDateTime.now()
            );
            when(omsReturnRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsReturnService.processInspectionResult(message))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_RETURN_NOT_FOUND);
        }

        @Test
        void 반품_상태가_PROCESSING이_아니면_OMS_INVALID_STATUS_예외가_발생한다() {
            // given
            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getStatus()).thenReturn(OmsReturnStatus.REQUESTED);

            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 1L, 10L, "PASSED", "CUSTOMER",
                    List.of(1L), List.of(), "메모", LocalDateTime.now()
            );
            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when & then
            assertThatThrownBy(() -> omsReturnService.processInspectionResult(message))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_INVALID_STATUS);
        }
    }

    @Test
    void duplicateJudgementIsRejectedBeforeMutatingItems() {
        OmsReturn omsReturn = mock(OmsReturn.class);
        when(omsReturnRepository.findByOmsOrderIdWithDetails(10L)).thenReturn(Optional.of(omsReturn));
        ReturnJudgementRequest request = new ReturnJudgementRequest(null, List.of(
                new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.APPROVE_LOGISTICS, null),
                new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.REJECT, "duplicate")
        ));
        assertThatThrownBy(() -> omsReturnService.judgeReturn(10L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OmsErrorCode.OMS_INVALID_STATUS);
    }

    @Test
    void wmsApprovalOutsideReturnIsRejected() {
        OmsReturn omsReturn = mock(OmsReturn.class);
        when(omsReturn.getStatus()).thenReturn(OmsReturnStatus.PROCESSING);
        when(omsReturn.getItems()).thenReturn(List.of());
        when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(omsReturn));
        WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                UUID.randomUUID(), 1L, 10L, "APPROVED", "CUSTOMER", List.of(999L), List.of(), null, LocalDateTime.now());
        assertThatThrownBy(() -> omsReturnService.processInspectionResult(message))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(OmsErrorCode.OMS_INVALID_STATUS);
    }

    @Nested
    @DisplayName("환불 완료 처리 예외 (completeRefund)")
    class CompleteRefundExceptionTest {

        @Test
        void 반품이_존재하지_않으면_OMS_RETURN_NOT_FOUND_예외가_발생한다() {
            // given
            PaymentRefundCompletedMessage message = new PaymentRefundCompletedMessage(
                    UUID.randomUUID(), 999L, 10000L, System.currentTimeMillis()
            );
            when(omsReturnRepository.findByIdWithDetails(999L)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> omsReturnService.completeRefund(message))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OmsErrorCode.OMS_RETURN_NOT_FOUND);
        }

        @Test
        void 환불_금액이_일치하지_않으면_IllegalStateException_예외가_발생한다() {
            // given
            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getTotalRefundAmount()).thenReturn(10000L);

            PaymentRefundCompletedMessage message = new PaymentRefundCompletedMessage(
                    UUID.randomUUID(), 1L, 5000L, System.currentTimeMillis()
            );
            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when & then
            assertThatThrownBy(() -> omsReturnService.completeRefund(message))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("환불 금액이 일치하지 않습니다.");
        }
    }
}
