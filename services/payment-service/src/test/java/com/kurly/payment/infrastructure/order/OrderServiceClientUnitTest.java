package com.kurly.payment.infrastructure.order;

import com.kurly.common.exception.BusinessException;
import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.exception.OrderNotFoundException;
import com.kurly.payment.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceClientUnitTest {

    private static final String FETCH_PATH = "/internal/v1/orders/501";
    private static final String COMPLETE_PATH = "/internal/v1/orders/501/complete-pay";

    /** 주문 담당자가 제공한 명세의 응답 형태. */
    private static final String ORDER_BODY = """
            {"orderId":501,"userId":1001,"amount":32000,"status":"PAYMENT_PENDING","reservationToken":"rsv_xxx"}""";

    private StubHttpServer stub;

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.close();
        }
        RequestContextHolder.resetRequestAttributes();
    }

    private OrderServiceClient client() {
        return new OrderServiceClient(stub.baseUrl());
    }

    private static void givenIncomingToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", token);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Nested
    @DisplayName("주문 조회")
    class FetchTest {

        @Test
        void 명세대로_금액과_소유자를_읽는다() {
            stub = new StubHttpServer().stub(FETCH_PATH, 200, ORDER_BODY);

            OrderClient.OrderSnapshot snapshot = client().fetch(501L);

            assertThat(snapshot.orderId()).isEqualTo(501L);
            assertThat(snapshot.ownerUserId()).isEqualTo(1001L);
            assertThat(snapshot.totalAmount()).isEqualTo(32_000L);
            assertThat(snapshot.payable()).isTrue();
        }

        @Test
        void PAYMENT_PENDING이_아니면_결제할_수_없는_상태로_본다() {
            stub = new StubHttpServer().stub(FETCH_PATH, 200, """
                    {"orderId":501,"userId":1001,"amount":32000,"status":"PAID"}""");

            assertThat(client().fetch(501L).payable()).isFalse();
        }

        @Test
        void ApiResponse로_감싸_와도_읽는다() {
            // 명세는 감싸지 않은 형태지만 팀 컨벤션은 ApiResponse다. 확정 전까지 양쪽을 받는다.
            stub = new StubHttpServer().stub(FETCH_PATH, 200, """
                    {"status":"SUCCESS","message":"조회","data":%s,"error":null}""".formatted(ORDER_BODY));

            assertThat(client().fetch(501L).totalAmount()).isEqualTo(32_000L);
        }

        @Test
        void 사용자_토큰을_그대로_전파한다() {
            // 주문 조회는 소유자 확인이 걸린 내부 API라 토큰 없이는 401이 된다.
            stub = new StubHttpServer().stub(FETCH_PATH, 200, ORDER_BODY);
            givenIncomingToken("Bearer test-access-token");

            client().fetch(501L);

            assertThat(stub.received(FETCH_PATH).authorization()).isEqualTo("Bearer test-access-token");
        }
    }

    @Nested
    @DisplayName("주문 조회 실패")
    class FetchFailureTest {

        @Test
        void 없거나_만료된_주문은_404로_받는다() {
            stub = new StubHttpServer().stub(FETCH_PATH, 404, """
                    {"error":"ORDER_NOT_FOUND"}""");

            assertThatThrownBy(() -> client().fetch(501L)).isInstanceOf(OrderNotFoundException.class);
        }

        @Test
        void 서버_오류면_결제를_진행하지_않는다() {
            // 금액·소유자를 확인하지 못하면 위변조 검증을 할 수 없다.
            stub = new StubHttpServer().stub(FETCH_PATH, 500, "{}");

            assertThatThrownBy(() -> client().fetch(501L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("주문 정보를 불러오는 중");
        }

        @Test
        void 응답_형식이_예상과_다르면_거부한다() {
            stub = new StubHttpServer().stub(FETCH_PATH, 200, """
                    {"unexpected":true}""");

            assertThatThrownBy(() -> client().fetch(501L)).isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("결제 완료 통보")
    class CompletePaymentTest {

        @Test
        void 정상_통보는_예외_없이_끝난다() {
            stub = new StubHttpServer().stub(COMPLETE_PATH, 200, null);

            client().completePayment(501L);

            assertThat(stub.received(COMPLETE_PATH).method()).isEqualTo("POST");
        }

        @Test
        void 상태_409는_주문_만료로_해석해_보상_취소를_유발한다() {
            stub = new StubHttpServer().stub(COMPLETE_PATH, 409, "{}");

            assertThatThrownBy(() -> client().completePayment(501L))
                    .isInstanceOf(OrderClient.OrderAlreadyExpiredException.class);
        }

        @Test
        void 그_밖의_실패는_서버_오류로_올린다() {
            stub = new StubHttpServer().stub(COMPLETE_PATH, 500, "{}");

            assertThatThrownBy(() -> client().completePayment(501L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("주문 정보 처리 중");
        }
    }
}
