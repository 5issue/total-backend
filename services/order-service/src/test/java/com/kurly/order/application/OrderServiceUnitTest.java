package com.kurly.order.application;

import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.order.domain.claim.OrderClaimRepository;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderItem;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.domain.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    OrderClaimRepository orderClaimRepository;
    @Mock
    ApplicationEventPublisher eventPublisher;
    @Mock
    OrderExternalService externalService;
    @InjectMocks
    OrderService orderService;

    private AuthenticatedPrincipal me;

    @BeforeEach
    void setUp() {
        me = new AuthenticatedPrincipal(1L, Role.USER);
    }

    @Nested
    @DisplayName("결제 요청 정상 테스트")
    class PlaceOrderTest {

        @Test
        void 주문서를_5분_결제대기_상태로_전환한다() {
            Order order = Order.createCheckout(
                    "O202609080001",
                    1L,
                    "reservation",
                    LocalDateTime.now().plusMinutes(15),
                    0L,
                    List.of(OrderItem.create(10L, 20L, 30L, "샐러드", null, StorageType.CHILLED, 2, 16000L))
            );
            // 비관적 락 조회 Mocking
            when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));

            var response = orderService.placeOrder(me, 1L);

            assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(response.expiresAt()).isAfter(LocalDateTime.now().plusMinutes(4));
            assertThat(response.expiresAt()).isBefore(LocalDateTime.now().plusMinutes(6));
        }
    }
}