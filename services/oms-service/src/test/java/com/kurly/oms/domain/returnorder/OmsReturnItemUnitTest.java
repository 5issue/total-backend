package com.kurly.oms.domain.returnorder;

import com.kurly.oms.domain.common.StorageType;
import com.kurly.oms.domain.order.OmsOrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OmsReturnItemUnitTest {

    @Nested
    @DisplayName("OmsReturnItem 생성 테스트")
    class CreateTest {

        @Test
        void PENDING_상태로_초기화된다() {
            // given
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderItemMock.getId()).thenReturn(101L);

            // when
            OmsReturnItem returnItem = OmsReturnItem.create(omsOrderItemMock);

            // then
            assertThat(returnItem.getDecision()).isEqualTo(ReturnDecision.PENDING);
            assertThat(returnItem.getOmsOrderItemId()).isEqualTo(101L);
        }
    }
    
    @Nested
    @DisplayName("반품 품목 판정 테스트")
    class JudgeTest {

        @ParameterizedTest
        @EnumSource(value = ReturnDecision.class, names = {"APPROVE_COLDCHAIN", "APPROVE_LOGISTICS"})
        void 승인_판정_시_상태가_정상_변경된다(ReturnDecision decision) {
            // given
            OmsOrderItem omsOrderItemMock = mock(OmsOrderItem.class);
            when(omsOrderItemMock.getId()).thenReturn(101L);
            OmsReturnItem returnItem = OmsReturnItem.create(omsOrderItemMock);

            // when
            returnItem.judge(decision, null);

            // then
            assertThat(returnItem.getDecision()).isEqualTo(decision);
        }

        @Test
        void REJECT_판정_시_사유가_정상_기록된다() {
            // given
            OmsOrderItem orderItem = OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM_TEMPERATURE, 1, 5000L);
            OmsReturnItem returnItem = OmsReturnItem.create(orderItem);

            // when
            returnItem.judge(ReturnDecision.REJECT, "고객 단순 변심 기간 경과");

            // then
            assertThat(returnItem.getDecision()).isEqualTo(ReturnDecision.REJECT);
            assertThat(returnItem.getRejectReason()).isEqualTo("고객 단순 변심 기간 경과");
        }
    }

}