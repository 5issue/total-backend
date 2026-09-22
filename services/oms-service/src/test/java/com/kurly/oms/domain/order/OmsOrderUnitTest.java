package com.kurly.oms.domain.order;

import com.kurly.oms.domain.common.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OmsOrderUnitTest {

    private OmsOrder createOmsOrder(List<OmsOrderItem> items) {
        return OmsOrder.create(
                500L, "0001", "evt-uuid-1234", 1L, 20000L,
                "name", "010-1234-5678", "06234", "서울시 강남구", "101호",
                items
        );
    }

    @Nested
    @DisplayName("OmsOrder 생성 테스트")
    class CreateTest {

        @Test
        void 초기_상태가_ORDER_RECEIVED로_저장된다() {
            // given
            OmsOrderItem item1 = OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM, 2, 5000L);
            OmsOrderItem item2 = OmsOrderItem.create(102L, 1002L, 2002L, StorageType.FROZEN, 1, 12000L);

            // when
            OmsOrder order = createOmsOrder(List.of(item1, item2));

            // then
            assertThat(order.getOrderId()).isEqualTo(500L);
            assertThat(order.getOrderNo()).isEqualTo("0001");
            assertThat(order.getStatus()).isEqualTo(OmsOrderStatus.ORDER_RECEIVED);
            assertThat(order.getPaidAmount()).isEqualTo(20000L);
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getItems().getFirst().getOmsOrder()).isSameAs(order);
        }
    }

    @Nested
    @DisplayName("재고 복원 필요 여부 테스트")
    class ReleaseRequiredTest {

        @ParameterizedTest
        @EnumSource(value = OmsOrderStatus.class)
        void 주문_상태에_따라_재고_복원_여부가_결정된다(OmsOrderStatus status) {
            // given
            OmsOrderItem mockOrderItem = mock(OmsOrderItem.class);
            OmsOrder order = createOmsOrder(List.of(mockOrderItem));

            // when
            order.updateStatus(status);

            // then
            if (status == OmsOrderStatus.STOCK_REQUESTED || status == OmsOrderStatus.RELEASE_INSTRUCTED) {
                assertThat(order.isReleaseRequired()).isTrue();
            } else {
                assertThat(order.isReleaseRequired()).isFalse();
            }
        }

    }


    @Nested
    @DisplayName("주문 취소 가능 여부 테스트")
    class CancelableTest {

        @ParameterizedTest
        @EnumSource(value = OmsOrderStatus.class)
        void 주문_상태에_따라_취소_가능_여부가_결정된다(OmsOrderStatus status) {
            // given
            OmsOrderItem mockOrderItem = mock(OmsOrderItem.class);
            OmsOrder order = createOmsOrder(List.of(mockOrderItem));

            // when
            order.updateStatus(status);

            // then
            if (status == OmsOrderStatus.RELEASE_INSTRUCTED || status == OmsOrderStatus.FULFILLED || status == OmsOrderStatus.CANCELLED) {
                assertThat(order.isCancelableOrder()).isFalse();
            } else {
                assertThat(order.isCancelableOrder()).isTrue();
            }
        }
    }

}