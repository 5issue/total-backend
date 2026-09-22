package com.kurly.order.infrastructure.messaging;

import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderDeliveryInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderPaymentCompletedEventUnitTest {
    @Test
    void 결제와_권역_식별자를_각각_올바른_필드에_담는다() {
        Order order = mock(Order.class);
        OrderDeliveryInfo deliveryInfo = mock(OrderDeliveryInfo.class);
        when(order.getPaymentId()).thenReturn(9001L);
        when(deliveryInfo.getRegionId()).thenReturn(3L);
        when(order.getItems()).thenReturn(java.util.List.of());

        OrderPaymentCompletedEvent event = OrderPaymentCompletedEvent.of(order, deliveryInfo);

        assertThat(event.paymentId()).isEqualTo(9001L);
        assertThat(event.regionId()).isEqualTo(3L);
    }
}
