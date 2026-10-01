package com.kurly.payment.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.kurly.payment.e2e.E2eHttp.baseUrl;
import static com.kurly.payment.e2e.E2eHttp.get;
import static com.kurly.payment.e2e.E2eHttp.post;
import static com.kurly.payment.e2e.E2eHttp.required;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 교차 시나리오 E2E-03 · E2E-04 (docs/QA_테스트케이스.md 9절).
 *
 * <p>결제와 주문을 함께 거치는 흐름이라 이미 떠 있는 환경을 상대로 HTTP로 검증한다.
 *
 * <p><b>선행 조건이 많다.</b> 토스 샌드박스 {@code paymentKey}는 결제위젯에서 사람이 발급받아야
 * 하고, 주문도 미리 만들어져 있어야 한다. 준비물이 없으면 건너뛴다 — 준비 부족을 Fail로 적으면
 * 보고서가 거짓이 된다.
 *
 * <p>실행:
 * <pre>
 * E2E_TEST=true E2E_BASE_URL=https://dev.cloudyim.store \
 * E2E_TOKEN_A=&lt;계정A&gt; E2E_TOKEN_B=&lt;계정B&gt; \
 * E2E_ORDER_PENDING=&lt;PAYMENT_PENDING 주문&gt; E2E_PAYMENT_KEY=&lt;샌드박스 키&gt; E2E_AMOUNT=&lt;주문금액&gt; \
 * ./gradlew :payment-service:test --tests '*PaymentOrderE2ETest'
 * </pre>
 */
@DisplayName("E2E — 결제 승인과 주문 연동")
@EnabledIfEnvironmentVariable(named = "E2E_TEST", matches = "true|stub")
class PaymentOrderE2ETest {

    /** {@code E2E_TEST=stub}일 때만 뜬다. 실제 환경을 상대할 때는 null이다. */
    private static E2eStubEnvironment stub;

    @BeforeAll
    static void startStubIfRequested() {
        if (!E2eHttp.stubMode()) {
            return;
        }
        stub = new E2eStubEnvironment();
        E2eHttp.override("E2E_BASE_URL", stub.baseUrl());
        E2eHttp.override("E2E_TOKEN_A", E2eStubEnvironment.TOKEN_A);
        E2eHttp.override("E2E_TOKEN_B", E2eStubEnvironment.TOKEN_B);
        E2eHttp.override("E2E_ORDER_PENDING", String.valueOf(E2eStubEnvironment.ORDER_PENDING));
        E2eHttp.override("E2E_ORDER_PAID", String.valueOf(E2eStubEnvironment.ORDER_PAID));
        E2eHttp.override("E2E_PAYMENT_KEY", E2eStubEnvironment.PAYMENT_KEY);
        E2eHttp.override("E2E_AMOUNT", String.valueOf(E2eStubEnvironment.AMOUNT));
    }

    @BeforeEach
    void resetStub() {
        if (stub != null) {
            // 결제·취소는 1회성이라 시나리오마다 되돌려야 서로의 순서에 의존하지 않는다.
            stub.reset();
        }
    }

    @AfterAll
    static void stopStub() {
        if (stub != null) {
            stub.close();
            stub = null;
        }
        E2eHttp.clearOverrides();
    }

    private static String paymentUrl() {
        return baseUrl("E2E_PAYMENT_URL");
    }

    private static String orderUrl() {
        return baseUrl("E2E_ORDER_URL");
    }

    private static String checkoutBody(String orderId, String paymentKey, String amount) {
        return """
                {"orderId":%s,"paymentMethod":"CARD","paymentKey":"%s","amount":%s}
                """.formatted(orderId, paymentKey, amount);
    }

    private static String uniqueKey() {
        return "e2e-" + System.nanoTime();
    }

    @Nested
    @DisplayName("E2E-03 결제 승인 → 주문 complete-pay 반영")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class CheckoutReflectedOnOrder {

        /**
         * 해피 케이스. 승인 응답의 {@code paymentId}(#56)와 주문의 PAID 전이를 함께 본다.
         *
         * <p>주문 전이는 결제가 주문 서비스의 {@code complete-pay}를 호출해 일어난다. 승인만
         * 200이고 주문이 PAID로 가지 않으면 <b>돈은 빠져나갔는데 주문이 모르는 상태</b>다.
         */
        @Test
        @Order(1)
        void 승인되면_paymentId가_오고_주문이_PAID로_전이한다() {
            String tokenA = required("E2E_TOKEN_A");
            String orderId = required("E2E_ORDER_PENDING");
            String body = checkoutBody(orderId, required("E2E_PAYMENT_KEY"), required("E2E_AMOUNT"));

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());

            assertThat(res.status()).isEqualTo(200);
            assertThat(res.data("paymentId")).isNotBlank();
            // 영수증 주소가 담기므로 중간 캐시에 남아서는 안 된다.
            assertThat(res.cacheControl()).contains("no-store");

            E2eHttp.Res order = get(orderUrl() + "/api/v1/orders/" + orderId, tokenA);

            assertThat(order.status()).isEqualTo(200);
            assertThat(order.bodyContains("PAID"))
                    .as("결제는 승인됐는데 주문이 PAID로 전이하지 않았다. 주문 인계 경로를 확인해야 한다")
                    .isTrue();
        }

        @Test
        @Order(2)
        void 같은_멱등키로_재요청하면_PG를_다시_부르지_않고_같은_응답을_준다() {
            String tokenA = required("E2E_TOKEN_A");
            String body = checkoutBody(required("E2E_ORDER_PENDING"),
                    required("E2E_PAYMENT_KEY"), required("E2E_AMOUNT"));
            String key = uniqueKey();

            E2eHttp.Res first = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", key);
            assumeTrue(first.status() == 200, "1회차 승인이 선행돼야 한다: " + first.body());

            E2eHttp.Res replay = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", key);

            // 네트워크 재시도가 이중 승인이 되면 안 된다. 저장된 응답을 그대로 돌려줘야 한다.
            assertThat(replay.status()).isEqualTo(first.status());
            assertThat(replay.data("paymentId")).isEqualTo(first.data("paymentId"));
        }

        @Test
        void 멱등키_헤더가_빠지면_400이다() {
            String tokenA = required("E2E_TOKEN_A");
            String body = checkoutBody(required("E2E_ORDER_PENDING"), "dummy-key", "1000");

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body);

            assertThat(res.status()).isEqualTo(400);
        }

        @Test
        void 금액을_위변조해_보내면_거부된다() {
            String tokenA = required("E2E_TOKEN_A");
            // 클라이언트가 보낸 금액을 믿으면 1원 결제로 상품을 가져갈 수 있다.
            String body = checkoutBody(required("E2E_ORDER_PENDING"), required("E2E_PAYMENT_KEY"), "1");

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());

            assertThat(res.status()).isNotEqualTo(200);
            assertThat(res.status()).isBetween(400, 499);
        }

        @Test
        void 소수_금액은_본문_파싱_단계에서_거부된다() {
            String tokenA = required("E2E_TOKEN_A");
            String body = """
                    {"orderId":%s,"paymentMethod":"CARD","paymentKey":"dummy","amount":1000.9}
                    """.formatted(required("E2E_ORDER_PENDING"));

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());

            // 잘라서 받으면 승인 금액과 요청 금액이 조용히 어긋난다.
            assertThat(res.status()).isEqualTo(400);
        }

        @Test
        void 음수_금액은_거부된다() {
            String tokenA = required("E2E_TOKEN_A");
            String body = checkoutBody(required("E2E_ORDER_PENDING"), "dummy", "-1");

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());

            assertThat(res.status()).isEqualTo(400);
        }

        @Test
        void 타인의_주문을_결제하려_하면_404다() {
            String tokenB = required("E2E_TOKEN_B");
            String body = checkoutBody(required("E2E_ORDER_PENDING"),
                    required("E2E_PAYMENT_KEY"), required("E2E_AMOUNT"));

            E2eHttp.Res res = post(paymentUrl() + "/api/v1/payments/checkout", tokenB, body,
                    "Idempotency-Key", uniqueKey());

            // 403으로 답하면 그 주문이 존재한다는 사실을 알려주게 된다.
            assertThat(res.status()).isEqualTo(404);
        }

        @Test
        @Order(3)
        void 같은_주문을_다른_멱등키로_두_번_결제하면_두_번째가_거부된다() {
            String tokenA = required("E2E_TOKEN_A");
            String body = checkoutBody(required("E2E_ORDER_PENDING"),
                    required("E2E_PAYMENT_KEY"), required("E2E_AMOUNT"));

            E2eHttp.Res first = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());
            assumeTrue(first.status() == 200, "1회차 승인이 선행돼야 한다: " + first.body());

            // 멱등키가 다르면 멱등 저장소는 새 요청으로 본다. 중복을 막는 것은 결제 도메인의
            // 상태 검증이어야 한다.
            E2eHttp.Res second = post(paymentUrl() + "/api/v1/payments/checkout", tokenA, body,
                    "Idempotency-Key", uniqueKey());

            assertThat(second.status())
                    .as("같은 주문이 두 번 결제됐다. 이중 청구다")
                    .isNotEqualTo(200);
        }
    }

    @Nested
    @DisplayName("E2E-04 주문 취소 → 결제 취소 연동")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class OrderCancelPropagatesToPayment {

        private static String cancelBody(String reasonCode, String detail) {
            return detail == null
                    ? "{\"reasonCode\":\"%s\"}".formatted(reasonCode)
                    : "{\"reasonCode\":\"%s\",\"reasonDetail\":\"%s\"}".formatted(reasonCode, detail);
        }

        /**
         * 해피 케이스.
         *
         * <p><b>결제 상태는 공개 API로 관측할 수 없다.</b> 영수증 응답({@code ReceiptResponse})에
         * 상태 필드가 없고, 결제 취소는 내부 엔드포인트라 외부에서 막혀 있다. 그래서 여기서는
         * 주문 전이와 "두 번째 취소가 거부되는지"까지만 검증한다. 결제 측 전이 확인은 DB나
         * 상태를 노출하는 조회 수단이 필요하다 — 보고서의 개선 항목이다.
         */
        @Test
        @Order(1)
        void PAID_주문을_취소하면_200이고_주문이_취소로_전이한다() {
            String tokenA = required("E2E_TOKEN_A");
            String orderId = required("E2E_ORDER_PAID");

            E2eHttp.Res res = post(orderUrl() + "/api/v1/orders/" + orderId + "/cancel", tokenA,
                    cancelBody("CNL01", null));

            assertThat(res.status()).isEqualTo(200);

            E2eHttp.Res order = get(orderUrl() + "/api/v1/orders/" + orderId, tokenA);
            assertThat(order.status()).isEqualTo(200);
            assertThat(order.bodyContains("CANCEL"))
                    .as("취소는 200인데 주문 상태가 취소로 보이지 않는다. 응답=%s", order.body())
                    .isTrue();
        }

        @Test
        @Order(2)
        void 이미_취소한_주문을_다시_취소하면_거부된다() {
            String tokenA = required("E2E_TOKEN_A");
            String orderId = required("E2E_ORDER_PAID");
            String url = orderUrl() + "/api/v1/orders/" + orderId + "/cancel";

            // 앞 시나리오의 실행 순서에 의존하지 않도록 1회차를 이 테스트 안에서 만든다.
            E2eHttp.Res first = post(url, tokenA, cancelBody("CNL01", null));
            assumeTrue(first.status() == 200,
                    "1회차 취소가 선행돼야 한다. 이미 취소된 주문이라면 다른 주문을 지정한다: " + first.body());

            E2eHttp.Res second = post(url, tokenA, cancelBody("CNL01", null));

            // 두 번 환불되면 그대로 손실이다.
            assertThat(second.status()).isNotEqualTo(200);
        }

        @Test
        void PAID가_아닌_주문을_취소하면_상태_오류다() {
            String tokenA = required("E2E_TOKEN_A");
            String orderId = required("E2E_ORDER_PENDING");

            E2eHttp.Res res = post(orderUrl() + "/api/v1/orders/" + orderId + "/cancel", tokenA,
                    cancelBody("CNL01", null));

            assertThat(res.status()).isNotEqualTo(200);
            assertThat(res.bodyContains("ORD_INVALID_STATUS"))
                    .as("상태 검증 오류 코드가 와야 한다. 응답=%s", res.body())
                    .isTrue();
        }

        @Test
        void 정의되지_않은_취소_사유_코드는_거부된다() {
            String tokenA = required("E2E_TOKEN_A");

            E2eHttp.Res res = post(
                    orderUrl() + "/api/v1/orders/" + required("E2E_ORDER_PAID") + "/cancel",
                    tokenA, cancelBody("XXX99", null));

            assertThat(res.status()).isNotEqualTo(200);
            assertThat(res.bodyContains("ORD_INVALID_REASON_CODE")).isTrue();
        }

        @Test
        void 상세가_필요한_사유인데_누락하면_거부된다() {
            String tokenA = required("E2E_TOKEN_A");

            // CNL99는 상세가 필수다. 비워 두면 왜 취소됐는지 기록이 남지 않는다.
            E2eHttp.Res res = post(
                    orderUrl() + "/api/v1/orders/" + required("E2E_ORDER_PAID") + "/cancel",
                    tokenA, cancelBody("CNL99", null));

            assertThat(res.status()).isNotEqualTo(200);
            assertThat(res.bodyContains("ORD_INVALID_REASON_DETAIL")).isTrue();
        }

        @Test
        void 타인의_주문을_취소하려_하면_차단된다() {
            String tokenB = required("E2E_TOKEN_B");
            String orderId = required("E2E_ORDER_PAID");

            E2eHttp.Res res = post(orderUrl() + "/api/v1/orders/" + orderId + "/cancel", tokenB,
                    cancelBody("CNL01", null));

            assertThat(res.status())
                    .as("타인이 남의 주문을 취소할 수 있으면 안 된다. 응답=%s %s", res.status(), res.body())
                    .isIn(403, 404);
        }
    }
}
