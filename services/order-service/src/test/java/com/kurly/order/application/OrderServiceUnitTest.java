package com.kurly.order.application;

import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.common.exception.BusinessException;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.claim.OrderClaimRepository;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.common.StorageType;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderDeliveryInfoRepository;
import com.kurly.order.domain.order.OrderItem;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.domain.order.OrderStatus;
import com.kurly.order.presentation.dto.CartResponseDto;
import com.kurly.order.presentation.dto.CheckoutInventoryResponseDto;
import com.kurly.order.presentation.dto.CheckoutRequestDto;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceUnitTest {

    @Mock
    OrderRepository orderRepository;
    @Mock
    CartRepository cartRepository;
    @Mock
    OrderClaimRepository orderClaimRepository;
    @Mock
    OrderDeliveryInfoRepository orderDeliveryInfoRepository;
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
    @DisplayName("주문서 생성")
    class CheckoutTest {

        @Test
        void 배송지가_없으면_재고를_선점하지_않는다() {
            Cart cart = cartWithItems(new CartItemSpec(1L, 10L, 1));
            when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
            when(externalService.getAddress(1L, 100L)).thenReturn(null);

            assertThatThrownBy(() -> orderService.checkout(me, new CheckoutRequestDto(List.of(1L))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_NOT_FOUND_ADDRESS);
            verify(externalService, never()).holdInventory(anyString(), anyList());
        }

        @Test
        void 응답에_중복_품목이_있으면_불완전한_응답으로_거부한다() {
            Cart cart = cartWithItems(new CartItemSpec(1L, 10L, 1), new CartItemSpec(2L, 11L, 1));
            CartResponseDto.Address address = new CartResponseDto.Address(
                    100L, "집", "홍길동", "01000000000", "12345", "서울시", "101호");
            CheckoutInventoryResponseDto.Item duplicate = new CheckoutInventoryResponseDto.Item(
                    10L, 101L, 1001L, "샐러드", null, StorageType.CHILLED, 1, 1000L);
            when(cartRepository.findByMemberIdForUpdate(1L)).thenReturn(Optional.of(cart));
            when(externalService.getAddress(1L, 100L)).thenReturn(address);
            when(orderRepository.findActiveCheckoutForUpdate(1L)).thenReturn(Optional.empty());
            when(externalService.holdInventory(anyString(), anyList()))
                    .thenReturn(new CheckoutInventoryResponseDto(null, null, 0L, List.of(duplicate, duplicate)));

            assertThatThrownBy(() -> orderService.checkout(me, new CheckoutRequestDto(List.of(1L, 2L))))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
            verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any());
        }

        private Cart cartWithItems(CartItemSpec... specs) {
            Cart cart = Cart.create(1L);
            cart.updateDeliveryAddress(100L, null, null);
            for (CartItemSpec spec : specs) {
                CartItem item = CartItem.create(spec.productId(), StorageType.CHILLED, spec.quantity());
                ReflectionTestUtils.setField(item, "id", spec.id());
                cart.addItem(item);
            }
            return cart;
        }

        private record CartItemSpec(Long id, Long productId, Integer quantity) {
        }
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
