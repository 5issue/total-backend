package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OmsReturnUnitTest {

    @Nested
    @DisplayName("OmsReturn 생성 테스트")
    class CreateTest {

        @Test
        void OmsOrder로부터_정상_생성된다() {
            // given
            OmsOrderItem item1 = OmsOrderItem.create(101L, 1001L, 2001L, StorageType.FROZEN, 2, 10000L);
            OmsOrder order = OmsOrder.create(
                    500L, "O202609210001", "evt-uuid-1234", 1L, 20000L,
                    "홍길동", "010-1234-5678", "06234", "서울시 강남구", "101호",
                    List.of(item1)
            );
            ReflectionTestUtils.setField(order, "id", 10L);

            // when
            OmsReturn omsReturn = OmsReturn.createFromOrder(order);

            // then
            assertThat(omsReturn.getOmsOrderId()).isEqualTo(10L);
            assertThat(omsReturn.getOrderId()).isEqualTo(500L);
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.REQUESTED);
            assertThat(omsReturn.getItems()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("반품 판정 적용 테스트")
    class ApplyJudgementTest {

        @Test
        void 자체_폐기_승인만_있으면_COMPLETED_상태가_된다() {
            // given
            OmsOrder omsOrderMock = mock(OmsOrder.class);
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderMock.getItems()).thenReturn(List.of(omsOrderItemMock));
            OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrderMock);

            // when
            omsReturn.applyJudgement("냉동/냉장 제품 폐기 승인", true, false);

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.COMPLETED);
            assertThat(omsReturn.getAdminNote()).isEqualTo("냉동/냉장 제품 폐기 승인");
        }

        @Test
        void 역물류_승인이_있으면_PROCESSING_상태가_된다() {
            // given
            OmsOrder omsOrderMock = mock(OmsOrder.class);
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderMock.getItems()).thenReturn(List.of(omsOrderItemMock));
            OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrderMock);

            // when
            omsReturn.applyJudgement("상온 제품 수거 승인", true, true);

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.PROCESSING);
            assertThat(omsReturn.getAdminNote()).isEqualTo("상온 제품 수거 승인");
        }

        @Test
        void 승인_없으면_REJECTED_상태가_된다() {
            // given
            OmsOrder omsOrderMock = mock(OmsOrder.class);
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderMock.getItems()).thenReturn(List.of(omsOrderItemMock));
            OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrderMock);

            // when
            omsReturn.applyJudgement("전 제품 반려", false, false);

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.REJECTED);
            assertThat(omsReturn.getAdminNote()).isEqualTo("전 제품 반려");
        }
    }

    @Nested
    @DisplayName("환불금 정산 테스트")
    class RefundTest {

        @Test
        void 검수_성공_시_COMPLETED_상태로_전환된다() {
            // given
            OmsOrder omsOrderMock = mock(OmsOrder.class);
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderMock.getItems()).thenReturn(List.of(omsOrderItemMock));
            OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrderMock);

            // when
            omsReturn.recordRefund(4000L, 1000L);

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.COMPLETED);
            assertThat(omsReturn.getTotalRefundAmount()).isEqualTo(4000L);
            assertThat(omsReturn.getDeductedShippingFee()).isEqualTo(1000L);
        }

        @Test
        void 검수_반려_시_REJECTED_상태로_전환된다() {
            // given
            OmsOrder omsOrderMock = mock(OmsOrder.class);
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderMock.getItems()).thenReturn(List.of(omsOrderItemMock));
            OmsReturn omsReturn = OmsReturn.createFromOrder(omsOrderMock);

            // when
            omsReturn.recordInspectionFailure();

            // then
            assertThat(omsReturn.getStatus()).isEqualTo(OmsReturnStatus.REJECTED);
            assertThat(omsReturn.getTotalRefundAmount()).isEqualTo(0L);
            assertThat(omsReturn.getDeductedShippingFee()).isEqualTo(0L);
        }
    }

}