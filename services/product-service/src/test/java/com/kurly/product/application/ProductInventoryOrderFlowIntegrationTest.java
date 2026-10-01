package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 주문 흐름(선점 → 확정 → 해제/복구)에서 재고 정합성이 지켜지는지 확인한다.
 *
 * <p>hold/release 의 동시성은 {@link ProductInventoryConcurrencyIntegrationTest} 가 다룬다. 여기서는 그 테스트에
 * 없는 confirm / restore 와, 확정된 예약을 보호하는 release 의 정상 동작을 시나리오 단위로 본다.
 * 예외가 던져지는 흐름은 {@link ProductInventoryOrderFlowIntegrationExceptionTest} 에 둔다.
 * 준비 코드와 실행 방법은 {@link ProductInventoryOrderFlowIntegrationTestSupport} 를 참고한다.
 */
@EnabledIfEnvironmentVariable(named = "PRODUCT_INTEGRATION_TEST", matches = "true")
class ProductInventoryOrderFlowIntegrationTest extends ProductInventoryOrderFlowIntegrationTestSupport {

    @Nested
    @DisplayName("주문 확정(confirm) 정상 테스트")
    class ConfirmTest {

        @Test
        void 선점한_재고를_확정하면_DB와_Redis의_선점_수량이_같고_확정_이벤트가_한_건_남는다() {
            long productId = productWithStock(BASE_QUANTITY);
            String token = newToken();
            long orderId = newOrderId();

            productInventoryService.hold(token, items(productId, 3));
            productInventoryService.confirm(token, orderId, items(productId, 3));

            assertThat(dbReserved(productId)).isEqualTo(3);
            assertThat(redisReserved(productId)).isEqualTo(3);
            assertThat(reservationStatus(token)).isEqualTo("CONFIRM");
            assertThat(outboxStatuses(orderId)).containsExactly("CONFIRMED");
        }

        @Test
        void 같은_확정_요청이_중복으로_도착해도_재고는_한_번만_잡힌다() {
            long productId = productWithStock(BASE_QUANTITY);
            String token = newToken();
            long orderId = newOrderId();
            productInventoryService.hold(token, items(productId, 3));
            productInventoryService.confirm(token, orderId, items(productId, 3));

            assertThatCode(() -> productInventoryService.confirm(token, orderId, items(productId, 3)))
                    .doesNotThrowAnyException();

            assertThat(dbReserved(productId)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("선점 해제(release) 정상 테스트")
    class ReleaseTest {

        @Test
        void 이미_확정된_예약에_늦게_도착한_해제는_무시되고_확정된_재고는_풀리지_않는다() {
            long productId = productWithStock(BASE_QUANTITY);
            String token = newToken();
            productInventoryService.hold(token, items(productId, 3));
            productInventoryService.confirm(token, newOrderId(), items(productId, 3));

            assertThatCode(() -> productInventoryService.release(token)).doesNotThrowAnyException();

            assertThat(reservationStatus(token)).isEqualTo("CONFIRM");
            assertThat(redisReserved(productId)).isEqualTo(3);
            assertThat(dbReserved(productId)).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("주문 취소 복구(restore) 정상 테스트")
    class RestoreTest {

        @Test
        void 복구하면_그_주문의_선점만_풀리고_같은_복구가_중복돼도_다른_주문의_선점은_유지된다() {
            long productId = productWithStock(BASE_QUANTITY);
            long canceledOrderId = newOrderId();
            long otherOrderId = newOrderId();
            confirmOrder(canceledOrderId, productId, 3);
            confirmOrder(otherOrderId, productId, 2);

            productInventoryService.restore(canceledOrderId, items(productId, 3));

            assertThat(dbReserved(productId)).isEqualTo(2);
            assertThat(redisReserved(productId)).isEqualTo(2);
            assertThat(outboxStatuses(canceledOrderId)).containsExactly("CONFIRMED", "RESTORED");

            // 같은 복구 이벤트가 한 번 더 도착해도, 이미 풀린 3개를 다시 빼서 다른 주문 몫(2개)까지 깎으면 안 된다.
            assertThatCode(() -> productInventoryService.restore(canceledOrderId, items(productId, 3)))
                    .doesNotThrowAnyException();

            assertThat(dbReserved(productId)).isEqualTo(2);
            assertThat(redisReserved(productId)).isEqualTo(2);
        }
    }
}
