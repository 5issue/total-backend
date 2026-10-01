package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 주문 흐름에서 예외가 던져지는 경우의 재고 정합성을 확인한다(팀 컨벤션: 예외 테스트는 파일 분리).
 * 정상 흐름은 {@link ProductInventoryOrderFlowIntegrationTest}, 준비 코드와 실행 방법은
 * {@link ProductInventoryOrderFlowIntegrationTestSupport} 를 참고한다.
 */
@EnabledIfEnvironmentVariable(named = "PRODUCT_INTEGRATION_TEST", matches = "true")
class ProductInventoryOrderFlowIntegrationExceptionTest extends ProductInventoryOrderFlowIntegrationTestSupport {

    @Nested
    @DisplayName("주문 확정(confirm) 예외 테스트")
    class ConfirmExceptionTest {

        @Test
        void 재고가_모자란_확정은_거절되고_재고는_그대로이며_실패_이벤트만_남는다() {
            long productId = productWithStock(2);
            long orderId = newOrderId();

            assertThatThrownBy(() -> productInventoryService.confirm(newToken(), orderId, items(productId, 3)))
                    .isInstanceOfSatisfying(ProductException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.OUT_OF_STOCK));

            assertThat(dbReserved(productId)).isZero();
            // 확정 트랜잭션은 롤백되지만, 실패 이벤트는 REQUIRES_NEW 라 남아 있어야 주문 쪽이 실패를 알 수 있다.
            assertThat(outboxStatuses(orderId)).containsExactly("INSUFFICIENT_STOCK");
        }
    }
}
