package com.kurly.order.application;

import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderStatus;
import com.kurly.order.infrastructure.messaging.OrderInventoryConfirmEvent;
import com.kurly.order.infrastructure.messaging.OrderPaymentCompletedEvent;
import com.kurly.order.presentation.dto.CheckoutRequestDto;
import com.kurly.order.presentation.dto.CompletePayRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.item;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RecordApplicationEvents
class OrderServiceIntegrationTest extends OrderIntegrationTestSupport {

    @Autowired
    ApplicationEvents applicationEvents;

    @Nested
    @DisplayName("E2E-02 상품 조회부터 결제 승인까지 정상 통합 테스트")
    class OrderPaymentFlowTest {

        @Test
        void 상품을_조회하고_본인_장바구니_품목으로_체크아웃하면_주문서와_배송지_스냅샷을_저장한다() {
            Cart cart = savedCart(item(101L, 2));
            mockCheckoutExternalServices(101L, "샐러드", 8_000L);

            var response = orderService.checkout(me, new CheckoutRequestDto(List.of(cart.getItems().getFirst().getId())));

            flushAndClear();
            Order saved = orderRepository.findById(response.orderId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(OrderStatus.CHECKOUT_CREATED);
            assertThat(saved.getDeliveryInfo().getRecipientName()).isEqualTo("홍길동");
            verify(cartExternalService).getProducts(List.of(101L));
            verify(externalService).holdInventory(any(), anyList());
        }

        @Test
        void 장바구니에서_선택한_품목만_체크아웃하면_상품_가격과_수량으로_결제금액을_계산한다() {
            Cart cart = savedCart(item(101L, 2), item(202L, 1));
            mockCheckoutExternalServices(101L, "샐러드", 8_000L);

            var response = orderService.checkout(me, new CheckoutRequestDto(List.of(cart.getItems().getFirst().getId())));

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst().productId()).isEqualTo(101L);
            assertThat(response.paymentAmount()).isEqualTo(16_000L);
        }

        @Test
        void 체크아웃한_주문을_결제요청하면_결제대기로_전환하고_주문한_품목만_장바구니에서_제거한다() {
            Cart cart = savedCart(item(101L, 2), item(202L, 1));
            mockCheckoutExternalServices(101L, "샐러드", 8_000L);
            Long orderId = orderService.checkout(
                    me, new CheckoutRequestDto(List.of(cart.getItems().getFirst().getId()))).orderId();

            var response = orderService.placeOrder(me, orderId);

            flushAndClear();
            Cart persistedCart = cartRepository.findByMemberIdForUpdate(MEMBER_ID).orElseThrow();
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
            assertThat(persistedCart.getItems()).extracting(CartItem::getProductId).containsExactly(202L);
        }

        @Test
        void 결제서비스가_결제할_주문을_조회하면_결제대기_주문의_금액과_남은_재고예약시간을_반환한다() {
            Order order = checkoutAndPlaceOrder();

            var response = orderService.getForPayment(me, order.getId());

            assertThat(response.orderId()).isEqualTo(order.getId());
            assertThat(response.amount()).isEqualTo(16_000L);
            assertThat(response.remainingTimeoutSeconds()).isPositive();
        }

        @Test
        void 결제서비스가_유효한_승인결과를_보내면_주문을_PAID로_전환하고_재고차감_이벤트를_발행한다() {
            Order order = checkoutAndPlaceOrder();
            LocalDateTime paidAt = LocalDateTime.now();

            var response = orderService.completePay(
                    me, order.getId(), new CompletePayRequestDto(9001L, 16_000L, paidAt));

            flushAndClear();
            Order paidOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(response.orderStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(paidOrder.getPaymentId()).isEqualTo(9001L);
            assertThat(applicationEvents.stream(OrderPaymentCompletedEvent.class)).hasSize(1);
            assertThat(applicationEvents.stream(OrderInventoryConfirmEvent.class))
                    .singleElement()
                    .satisfies(event -> {
                        assertThat(event.reservationToken()).isEqualTo(paidOrder.getInventoryReservationToken());
                        assertThat(event.items()).singleElement()
                                .satisfies(item -> {
                                    assertThat(item.productId()).isEqualTo(101L);
                                    assertThat(item.quantity()).isEqualTo(2);
                                });
                    });
        }
    }
}
