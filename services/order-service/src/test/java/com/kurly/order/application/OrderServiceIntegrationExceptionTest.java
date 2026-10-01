package com.kurly.order.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.common.OrderErrorCode;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;
import com.kurly.order.presentation.dto.CheckoutRequestDto;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.address;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.item;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceIntegrationExceptionTest extends OrderIntegrationTestSupport {

    @Nested
    @DisplayName("E2E-02 상품 조회부터 결제 승인까지 예외 통합 테스트")
    class OrderPaymentFlowExceptionTest {

        @Test
        void 다른_회원이_소유한_주문서로_결제를_요청하면_소유권_검증에서_거부한다() {
            Order order = checkoutAndPlaceOrder();
            AuthenticatedPrincipal otherMember = new AuthenticatedPrincipal(2L, Role.USER);

            assertBusinessError(() -> orderService.placeOrder(otherMember, order.getId()), GlobalErrorCode.FORBIDDEN);
        }

        @Test
        void 상품서비스와_통신중_오류가_발생하면_체크아웃과_재고선점을_진행하지_않는다() {
            Cart cart = savedCart(item(101L, 2));
            when(externalService.getAddress(MEMBER_ID, 10L)).thenReturn(address());
            when(cartExternalService.getProducts(List.of(101L)))
                    .thenThrow(new ResourceAccessException("product-service timeout"));

            assertBusinessError(
                    () -> orderService.checkout(me, requestOf(cart)),
                    OrderErrorCode.ORD_INCOMPLETE_PRODUCT_RESPONSE);
            assertThat(orderRepository.findActiveCheckoutForUpdate(MEMBER_ID)).isEmpty();
            verify(externalService, never()).holdInventory(any(), anyList());
        }

        @Test
        void 체크아웃중_상품서비스가_재고부족을_응답하면_주문서를_생성하지_않는다() {
            Cart cart = savedCart(item(101L, 2));
            mockCheckoutExternalServices(101L, "샐러드", 8_000L);
            doThrow(HttpClientErrorException.create(
                    HttpStatus.CONFLICT, "insufficient stock", HttpHeaders.EMPTY, null, null))
                    .when(externalService).holdInventory(any(), anyList());

            assertBusinessError(
                    () -> orderService.checkout(me, requestOf(cart)),
                    OrderErrorCode.ORD_INSUFFICIENT_STOCK);
            assertThat(orderRepository.findActiveCheckoutForUpdate(MEMBER_ID)).isEmpty();
        }

        @Test
        void 결제서비스가_주문금액과_다른_승인금액을_보내면_PAID로_전환하지_않는다() {
            Order order = checkoutAndPlaceOrder();

            assertBusinessError(
                    () -> orderService.completePay(me, order.getId(),
                            new CompletePayRequestDto(9001L, 15_999L, LocalDateTime.now())),
                    OrderErrorCode.ORD_INVALID_PAYMENT_AMOUNT);
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        void 결제서비스가_재고예약_만료후_승인결과를_보내면_PAID로_전환하지_않는다() {
            Order order = checkoutAndPlaceOrder();
            LocalDateTime afterExpiration = order.getInventoryReservedUntil().plusSeconds(1);

            assertBusinessError(
                    () -> orderService.completePay(me, order.getId(),
                            new CompletePayRequestDto(9001L, 16_000L, afterExpiration)),
                    OrderErrorCode.ORD_EXPIRED_PAYMENT_TIMEOUT);
            assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                    .isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        private CheckoutRequestDto requestOf(Cart cart) {
            return new CheckoutRequestDto(List.of(cart.getItems().getFirst().getId()));
        }

        private void assertBusinessError(Runnable action, Object errorCode) {
            assertThatThrownBy(action::run)
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(errorCode);
        }
    }
}
