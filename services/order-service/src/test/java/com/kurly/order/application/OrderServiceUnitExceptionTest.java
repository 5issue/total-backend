package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.order.domain.claim.OrderClaimRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderItem;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import com.kurly.order.presentation.dto.ReturnRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class OrderServiceUnitExceptionTest {

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
    @DisplayName("주문 조회 예외 테스트")
    class GetOrderTest {

        @Test
        void 존재하지_않는_주문은_도메인_에러를_반환한다() {
            assertThatThrownBy(() -> orderService.getForPayment(me, 404L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_NOT_FOUND_ORDER);
        }
    }

    @Nested
    @DisplayName("반품 증빙 예외 테스트")
    class ReturnEvidenceTest {

        @Test
        void 증빙이_필요한_사유는_사진_없이_접수할_수_없다() {
            // RTN02는 사진 증빙 필수 사유
            ReturnRequestDto request = new ReturnRequestDto("RTN02", "상품 불량", List.of());

            assertThatThrownBy(() -> orderService.requestReturn(me, 1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_MISSING_RETURN_EVIDENCE);
        }
    }

    @Nested
    @DisplayName("결제 완료 예외 테스트")
    class CompletePayTest {

        @Test
        void 결제시각이_주문만료시간보다_이후면_예외를_반환한() {
            LocalDateTime now = LocalDateTime.now();
            OrderItem orderItemMock = mock(OrderItem.class);
            when(orderItemMock.getLineAmount()).thenReturn(2000L);
            Order order = Order.createCheckout("testNo", me.userId(), UUID.randomUUID(), now, null, List.of(orderItemMock));
            when(orderRepository.findByIdForUpdate(anyLong())).thenReturn(Optional.of(order));

            ReflectionTestUtils.setField(order, "id", 1L);

            order.markPaymentPending(now);

            assertThatThrownBy(() -> orderService.completePay(
                    me,
                    order.getId(),
                    new CompletePayRequestDto(2L, 2000L, now.plusSeconds(1))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_EXPIRED_PAYMENT_TIMEOUT);
        }
    }
}