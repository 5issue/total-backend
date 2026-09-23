package com.kurly.oms.domain.order;

import com.kurly.oms.domain.common.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OmsOrderItemUnitTest {

    @Nested
    @DisplayName("OmsOrderItem 생성 테스트")
    class CreateTest {

        @Test
        void 정상적으로_객체를_생성한다() {
            // when
            OmsOrderItem item = OmsOrderItem.create(101L, 1001L, 2001L, StorageType.ROOM_TEMPERATURE, 2, 5000L);

            // then
            assertThat(item.getOrderItemId()).isEqualTo(101L);
            assertThat(item.getProductId()).isEqualTo(1001L);
            assertThat(item.getSkuId()).isEqualTo(2001L);
            assertThat(item.getStorageType()).isEqualTo(StorageType.ROOM_TEMPERATURE);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualTo(5000L);
        }
    }
}