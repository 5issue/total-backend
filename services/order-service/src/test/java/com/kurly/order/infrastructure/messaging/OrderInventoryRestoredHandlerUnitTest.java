package com.kurly.order.infrastructure.messaging;

import com.kurly.order.application.OrderService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderInventoryRestoredHandlerUnitTest {

    @Mock
    OrderService orderService;

    @Test
    void 복구_성공일_때만_주문_취소를_완료한다() {
        OrderInventoryRestoredHandler handler = new OrderInventoryRestoredHandler(orderService);
        handler.handle(event("RESTORED"));
        handler.handle(event("ALREADY_RESTORED"));

        verify(orderService).completeCancel(501L, "rsv_test");
        verifyNoMoreInteractions(orderService);
    }

    private ProductInventoryRestoredEvent event(String status) {
        return new ProductInventoryRestoredEvent(UUID.randomUUID(), "product.inventory.restored",
                "rsv_test", 501L, status, LocalDateTime.now());
    }
}
