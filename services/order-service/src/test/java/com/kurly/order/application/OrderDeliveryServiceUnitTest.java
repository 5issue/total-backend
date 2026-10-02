package com.kurly.order.application;

import com.kurly.order.presentation.dto.DeliveryCompleteResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("배송 완료 처리")
class OrderDeliveryServiceUnitTest {

    private static final Long ORDER_ID = 17L;
    private static final Long MEMBER_ID = 1001L;

    @Mock OrderDeliveryRecorder recorder;
    @Mock FridgeClient fridgeClient;
    @InjectMocks OrderDeliveryService orderDeliveryService;

    private static OrderDeliveryRecorder.DeliveredOrder delivered() {
        return new OrderDeliveryRecorder.DeliveredOrder(
                ORDER_ID, MEMBER_ID, LocalDateTime.now(),
                List.of(new FridgeClient.FridgeItem(749L, 1),
                        new FridgeClient.FridgeItem(810L, 2)));
    }

    @Nested
    @DisplayName("정상 처리")
    class SuccessTest {

        @Test
        void 배송_완료로_전이하고_품목을_냉장고에_넣는다() {
            given(recorder.markDelivered(ORDER_ID)).willReturn(delivered());

            DeliveryCompleteResponseDto res = orderDeliveryService.completeDelivery(ORDER_ID);

            assertThat(res.orderId()).isEqualTo(ORDER_ID);
            assertThat(res.deliveryStatus()).isEqualTo("DELIVERED");
            assertThat(res.fridgeSynced()).isTrue();
            verify(fridgeClient).addItems(MEMBER_ID,
                    List.of(new FridgeClient.FridgeItem(749L, 1),
                            new FridgeClient.FridgeItem(810L, 2)));
        }

        @Test
        void 주문의_회원에게_넣는다_요청자가_관리자여도_마찬가지다() {
            given(recorder.markDelivered(ORDER_ID)).willReturn(delivered());

            orderDeliveryService.completeDelivery(ORDER_ID);

            // 호출자는 관리자지만 냉장고는 구매자의 것이다. 관리자 id를 쓰면 엉뚱한 사람에게 들어간다.
            verify(fridgeClient).addItems(org.mockito.ArgumentMatchers.eq(MEMBER_ID), any());
        }
    }

    @Nested
    @DisplayName("냉장고 적재 실패")
    class FridgeFailureTest {

        @Test
        void AI_호출이_실패해도_배송_완료는_되돌리지_않는다() {
            given(recorder.markDelivered(ORDER_ID)).willReturn(delivered());
            willThrow(new RuntimeException("AI 서버 타임아웃"))
                    .given(fridgeClient).addItems(anyLong(), any());

            DeliveryCompleteResponseDto res = orderDeliveryService.completeDelivery(ORDER_ID);

            // 되돌리면 반품 자격까지 함께 막힌다. 배송 완료는 주문 도메인의 사실이다.
            assertThat(res.deliveryStatus()).isEqualTo("DELIVERED");
        }

        @Test
        void 적재_실패를_응답에_드러낸다() {
            given(recorder.markDelivered(ORDER_ID)).willReturn(delivered());
            willThrow(new RuntimeException("AI 서버 5xx"))
                    .given(fridgeClient).addItems(anyLong(), any());

            DeliveryCompleteResponseDto res = orderDeliveryService.completeDelivery(ORDER_ID);

            // 조용히 성공으로 보이면 누락을 아무도 모른다. 사람이 보정해야 하므로 알려야 한다.
            assertThat(res.fridgeSynced()).isFalse();
        }

        @Test
        void 적재에_실패해도_자동으로_재시도하지_않는다() {
            given(recorder.markDelivered(ORDER_ID)).willReturn(delivered());
            willThrow(new RuntimeException("응답 끊김"))
                    .given(fridgeClient).addItems(anyLong(), any());

            orderDeliveryService.completeDelivery(ORDER_ID);

            // 수량을 누적하는 비멱등 요청이다. 응답이 끊겨도 이미 반영돼 있을 수 있어
            // 재시도하면 두 배가 된다(AI팀 합의).
            verify(fridgeClient, org.mockito.Mockito.times(1)).addItems(anyLong(), any());
        }
    }

    @Nested
    @DisplayName("상태 전이가 거절된 경우")
    class RejectedTest {

        @Test
        void 전이가_거절되면_냉장고를_건드리지_않는다() {
            // 이미 배송 완료된 주문, 결제되지 않은 주문, 없는 주문이 모두 여기로 떨어진다.
            willThrow(new IllegalStateException("이미 배송 완료"))
                    .given(recorder).markDelivered(ORDER_ID);

            assertThatThrownBy(() -> orderDeliveryService.completeDelivery(ORDER_ID))
                    .isInstanceOf(IllegalStateException.class);

            verify(fridgeClient, never()).addItems(anyLong(), any());
        }
    }
}
