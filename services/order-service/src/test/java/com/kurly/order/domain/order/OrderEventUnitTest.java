package com.kurly.order.domain.order;

import com.kurly.order.domain.common.StorageType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventUnitTest {

    @Test
    void 비동기_주문_이벤트에_재고_선점_토큰을_포함한다() {
        Order order = Order.createCheckout(
                "O202609120001",
                1L,
                "rsv_test",
                LocalDateTime.now().plusMinutes(15),
                0L,
                List.of(OrderItem.create(10L, 20L, 30L, "샐러드", null,
                        StorageType.CHILLED, 2, 16000L))
        );

        OrderEvent event = OrderEvent.of("order.inventory.release", order);

        assertThat(event.reservationToken()).isEqualTo("rsv_test");
    }
}
