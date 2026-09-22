package com.kurly.oms.application;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.domain.returnorder.*;
import com.kurly.oms.infrastructure.messaging.*;
import com.kurly.oms.presentation.dto.ReturnJudgementRequest;
import com.kurly.oms.presentation.dto.ReturnJudgementResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class OmsReturnServiceUnitTest {

    @Mock
    private OmsOrderRepository omsOrderRepository;

    @Mock
    private OmsReturnRepository omsReturnRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private OmsReturnCreator returnCreator;

    @InjectMocks
    private OmsReturnService omsReturnService;

    @Nested
    @DisplayName("반품 접수 (receiveReturn)")
    class ReceiveReturnTest {

        @Test
        void 반품_접수_메시지_수신시_OmsReturn을_정상_생성한다(CapturedOutput output) {
            // given

            OmsOrder orderMock = mock(OmsOrder.class);
            when(orderMock.getId()).thenReturn(10L);

            OrderReturnRequestedMessage event = new OrderReturnRequestedMessage(
                    UUID.randomUUID(), 500L, 1L, LocalDateTime.now()
            );

            when(omsOrderRepository.findByOrderId(500L)).thenReturn(Optional.of(orderMock));
            when(omsReturnRepository.existsByOmsOrderId(10L)).thenReturn(false);
            when(returnCreator.create(eq(500L), anyString())).thenReturn(mock(OmsReturn.class));

            // when
            omsReturnService.receiveReturn(event);

            // then
            verify(returnCreator).create(eq(500L), eq(event.eventId().toString()));
            assertThat(output.getAll()).contains("[OmsReturnService] OmsReturn 생성 완료");
        }

        @Test
        void 이미_반품_접수된_주문이면_저장하지_않고_경고_로그를_남긴다(CapturedOutput output) {
            // given
            OmsOrder orderMock = mock(OmsOrder.class);
            when(orderMock.getId()).thenReturn(10L);

            OrderReturnRequestedMessage event = new OrderReturnRequestedMessage(
                    UUID.randomUUID(), 500L, 1L, LocalDateTime.now()
            );

            when(omsOrderRepository.findByOrderId(500L)).thenReturn(Optional.of(orderMock));
            when(omsReturnRepository.existsByOmsOrderId(10L)).thenReturn(true);

            // when
            omsReturnService.receiveReturn(event);

            // then
            verify(returnCreator, never()).create(any(), any());
            assertThat(output.getAll()).contains("이미 반품 접수된 주문입니다");
        }
    }

    @Nested
    @DisplayName("반품 판정 (judgeReturn)")
    class JudgeReturnTest {

        private OmsReturnItem createReturnItemForJudge(Long omsOrderItemId) {
            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
            when(orderItemMock.getId()).thenReturn(omsOrderItemId);
            return OmsReturnItem.create(orderItemMock);
        }

        @Test
        void 콜드체인_승인만_있으면_즉시_환불_이벤트를_발행한다() {
            // given
            Long omsOrderId = 10L;
            OmsReturnItem returnItem = createReturnItemForJudge(1L);
            when(returnItem.getOrderItem().getUnitPrice()).thenReturn(5000L);
            when(returnItem.getOrderItem().getQuantity()).thenReturn(2);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(omsOrderId);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getItems()).thenReturn(List.of(returnItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    "냉동 제품 폐기 승인",
                    List.of(new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.APPROVE_COLDCHAIN, null))
            );

            // when
            ReturnJudgementResponse response = omsReturnService.judgeReturn(omsOrderId, request);

            // then
            assertThat(response.coldchainApprovedIds()).containsExactly(1L);
            assertThat(response.logisticsApprovedIds()).isEmpty();
            assertThat(response.rejectedItemIds()).isEmpty();

            verify(eventPublisher, times(1)).publishEvent(any(OmsRefundRequestedEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OmsReturnInspectionRequestedEvent.class));
            verify(returnMock).recordRefund(10000L, 0L);
        }

        @Test
        void 역물류_승인만_있으면_검수_요청_이벤트를_발행한다() {
            // given
            Long omsOrderId = 10L;
            OmsReturnItem returnItem = createReturnItemForJudge(1L);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(omsOrderId);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getItems()).thenReturn(List.of(returnItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    "상온 제품 수거",
                    List.of(new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.APPROVE_LOGISTICS, null))
            );

            // when
            ReturnJudgementResponse response = omsReturnService.judgeReturn(omsOrderId, request);

            // then
            assertThat(response.logisticsApprovedIds()).containsExactly(1L);
            assertThat(response.coldchainApprovedIds()).isEmpty();

            verify(eventPublisher, times(1)).publishEvent(any(OmsReturnInspectionRequestedEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OmsRefundRequestedEvent.class));
        }

        @Test
        void 콜드체인과_역물류_승인이_둘다_있으면_검수_요청_이벤트만_발행한다() {
            // given
            Long omsOrderId = 10L;
            OmsReturnItem coldItem = createReturnItemForJudge(1L);
            OmsReturnItem logisticsItem = createReturnItemForJudge(2L);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(omsOrderId);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getItems()).thenReturn(List.of(coldItem, logisticsItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    "혼합 판정",
                    List.of(
                            new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.APPROVE_COLDCHAIN, null),
                            new ReturnJudgementRequest.ItemJudgement(2L, ReturnDecision.APPROVE_LOGISTICS, null)
                    )
            );

            // when
            ReturnJudgementResponse response = omsReturnService.judgeReturn(omsOrderId, request);

            // then
            assertThat(response.coldchainApprovedIds()).containsExactly(1L);
            assertThat(response.logisticsApprovedIds()).containsExactly(2L);

            verify(eventPublisher, times(1)).publishEvent(any(OmsReturnInspectionRequestedEvent.class));
            verify(eventPublisher, never()).publishEvent(any(OmsRefundRequestedEvent.class));
        }

        @Test
        void 전체_REJECT_판정이면_이벤트를_발행하지_않는다() {
            // given
            Long omsOrderId = 10L;
            OmsReturnItem returnItem = createReturnItemForJudge(1L);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getItems()).thenReturn(List.of(returnItem));
            when(omsReturnRepository.findByOmsOrderIdWithDetails(omsOrderId)).thenReturn(Optional.of(returnMock));

            ReturnJudgementRequest request = new ReturnJudgementRequest(
                    "전 제품 반려",
                    List.of(new ReturnJudgementRequest.ItemJudgement(1L, ReturnDecision.REJECT, "고객 변심 기간 경과"))
            );

            // when
            ReturnJudgementResponse response = omsReturnService.judgeReturn(omsOrderId, request);

            // then
            assertThat(response.rejectedItemIds()).containsExactly(1L);
            assertThat(response.coldchainApprovedIds()).isEmpty();
            assertThat(response.logisticsApprovedIds()).isEmpty();

            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("검수 결과 처리 (processInspectionResult)")
    class ProcessInspectionResultTest {

        private OmsReturnItem createReturnItemForInspection(Long omsOrderItemId, long unitPrice, int quantity, ReturnDecision decision) {
            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
            when(orderItemMock.getId()).thenReturn(omsOrderItemId);
            when(orderItemMock.getUnitPrice()).thenReturn(unitPrice);
            when(orderItemMock.getQuantity()).thenReturn(quantity);
            OmsReturnItem item = OmsReturnItem.create(orderItemMock);
            item.judge(decision, null);
            return item;
        }

        private OmsReturnItem createReturnItemForInspectionReject() {
            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);
            OmsReturnItem item = OmsReturnItem.create(orderItemMock);
            item.judge(ReturnDecision.REJECT, null);
            return item;
        }

        @Test
        void 검수_합격_고객_귀책이면_배송비_차감_후_환불_이벤트를_발행한다(CapturedOutput output) {
            // given
            OmsReturnItem logisticsItem = createReturnItemForInspection(
                    1L, 5000L, 2, ReturnDecision.APPROVE_LOGISTICS);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(10L);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getStatus()).thenReturn(OmsReturnStatus.PROCESSING);
            when(returnMock.getItems()).thenReturn(List.of(logisticsItem));

            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 1L, 10L, "PASSED", "CUSTOMER",
                    List.of(1L), List.of(), "정상 수거 완료", LocalDateTime.now()
            );

            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when
            omsReturnService.processInspectionResult(message);

            // then
            ArgumentCaptor<OmsRefundRequestedEvent> captor = ArgumentCaptor.forClass(OmsRefundRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OmsRefundRequestedEvent event = captor.getValue();
            assertThat(event.refundAmount()).isEqualTo(7000L);
            assertThat(event.deductedFee()).isEqualTo(3000L);

            verify(returnMock).recordRefund(7000L, 3000L);
            assertThat(output.getAll()).contains("통합 환불 요청 이벤트 발행 완료");
        }

        @Test
        void 검수_합격_판매자_귀책이면_배송비_미차감_환불_이벤트를_발행한다(CapturedOutput output) {
            // given
            OmsReturnItem logisticsItem = createReturnItemForInspection(
                    1L, 5000L, 2, ReturnDecision.APPROVE_LOGISTICS);

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(10L);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getStatus()).thenReturn(OmsReturnStatus.PROCESSING);
            when(returnMock.getItems()).thenReturn(List.of(logisticsItem));

            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 1L, 10L, "PASSED", "SELLER",
                    List.of(1L), List.of(), "판매자 귀책", LocalDateTime.now()
            );

            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when
            omsReturnService.processInspectionResult(message);

            // then
            ArgumentCaptor<OmsRefundRequestedEvent> captor = ArgumentCaptor.forClass(OmsRefundRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OmsRefundRequestedEvent event = captor.getValue();
            assertThat(event.refundAmount()).isEqualTo(10000L);
            assertThat(event.deductedFee()).isEqualTo(0L);

            verify(returnMock).recordRefund(10000L, 0L);
            assertThat(output.getAll()).contains("통합 환불 요청 이벤트 발행 완료");
        }

        @Test
        void 검수_부분_승인시_콜드체인과_합격_역물류_품목만_합산하여_환불_이벤트를_발행한다(CapturedOutput output) {
            // given
            OmsReturnItem coldItem = createReturnItemForInspection(
                    1L, 5000L, 1, ReturnDecision.APPROVE_COLDCHAIN);

            OmsReturnItem passedLogisticsItem = createReturnItemForInspection(
                    2L, 3000L, 2, ReturnDecision.APPROVE_LOGISTICS);

            OmsReturnItem rejectedLogisticsItem = createReturnItemForInspectionReject();

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getOmsOrderId()).thenReturn(10L);
            when(returnMock.getOrderId()).thenReturn(500L);
            when(returnMock.getStatus()).thenReturn(OmsReturnStatus.PROCESSING);
            when(returnMock.getItems()).thenReturn(List.of(coldItem, passedLogisticsItem, rejectedLogisticsItem));

            // approvedItemIds에 2L만 포함, 3L은 rejectedItemIds
            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 1L, 10L, "PARTIAL", "CUSTOMER",
                    List.of(2L), List.of(3L), "부분 승인", LocalDateTime.now()
            );

            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when
            omsReturnService.processInspectionResult(message);

            // then
            // 환불 대상: 콜드체인 5000 + 역물류 합격 6000 = 11000, 고객 귀책 배송비 3000 차감 = 8000
            ArgumentCaptor<OmsRefundRequestedEvent> captor = ArgumentCaptor.forClass(OmsRefundRequestedEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());

            OmsRefundRequestedEvent event = captor.getValue();
            assertThat(event.refundAmount()).isEqualTo(8000L);
            assertThat(event.deductedFee()).isEqualTo(3000L);
            assertThat(event.omsOrderItemIds()).containsExactlyInAnyOrder(1L, 2L);

            verify(returnMock).recordRefund(8000L, 3000L);
            assertThat(output.getAll()).contains("통합 환불 요청 이벤트 발행 완료");
        }

        @Test
        void 검수_불합격_콜드체인_승인건이_없으면_inspectionFailure_처리한다(CapturedOutput output) {
            // given
            OmsReturnItem logisticsItem = createReturnItemForInspectionReject();

            OmsReturn returnMock = mock(OmsReturn.class);
            when(returnMock.getStatus()).thenReturn(OmsReturnStatus.PROCESSING);
            when(returnMock.getItems()).thenReturn(List.of(logisticsItem));

            WmsReturnInspectedMessage message = new WmsReturnInspectedMessage(
                    UUID.randomUUID(), 1L, 10L, "FAILED", null,
                    List.of(), List.of(1L), "검수 불합격", LocalDateTime.now()
            );

            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(returnMock));

            // when
            omsReturnService.processInspectionResult(message);

            // then
            verify(returnMock).recordInspectionFailure();
            verify(eventPublisher, never()).publishEvent(any());
            assertThat(output.getAll()).contains("검수 전량 불합격 처리");
        }
    }


    @Nested
    @DisplayName("환불 완료 처리 (completeRefund)")
    class CompleteRefundTest {

        @Test
        void 환불_금액이_일치하면_completeRefund를_호출한다() {
            // given
            OmsOrderItem orderItemMock = mock(OmsOrderItem.class);

            OmsOrder orderMock = mock(OmsOrder.class);
            when(orderMock.getId()).thenReturn(10L);
            when(orderMock.getOrderId()).thenReturn(500L);
            when(orderMock.getItems()).thenReturn(List.of(orderItemMock));

            OmsReturn omsReturn = OmsReturn.createFromOrder(orderMock, "test-event");
            omsReturn.recordRefund(10000L, 0L);

            PaymentRefundCompletedMessage message = new PaymentRefundCompletedMessage(
                    UUID.randomUUID(), 1L, 10000L, System.currentTimeMillis()
            );

            when(omsReturnRepository.findByIdWithDetails(1L)).thenReturn(Optional.of(omsReturn));

            // when
            omsReturnService.completeRefund(message);

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.COMPLETED);
        }
    }
}
