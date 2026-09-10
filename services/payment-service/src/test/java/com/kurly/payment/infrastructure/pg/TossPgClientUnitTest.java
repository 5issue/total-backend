package com.kurly.payment.infrastructure.pg;

import com.kurly.common.exception.BusinessException;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.exception.PaymentDeclinedException;
import com.kurly.payment.support.StubHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPgClientUnitTest {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    private static final String CANCEL_PATH = "/v1/payments/TOSS-KEY/cancel";
    private static final String SECRET_KEY = "test_sk_example";

    private static final String APPROVAL_BODY = """
            {"paymentKey":"TOSS-KEY","method":"카드","totalAmount":32000,
             "receipt":{"url":"https://dashboard.tosspayments.com/receipt/x"}}""";

    private StubHttpServer stub;

    @AfterEach
    void tearDown() {
        if (stub != null) {
            stub.close();
        }
    }

    private TossPgClient client() {
        return new TossPgClient(new TossPaymentProperties(stub.baseUrl(), SECRET_KEY));
    }

    @Nested
    @DisplayName("인증")
    class AuthTest {

        @Test
        void 시크릿_키_뒤에_콜론을_붙여_Base64로_보낸다() {
            // 콜론을 빼면 토스가 인증을 거부한다.
            stub = new StubHttpServer().stub(CONFIRM_PATH, 200, APPROVAL_BODY);

            client().approve("TOSS-KEY", 501L, 32_000L);

            String expected = "Basic " + Base64.getEncoder()
                    .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
            assertThat(stub.received(CONFIRM_PATH).authorization()).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("승인")
    class ApproveTest {

        @Test
        void 결제_수단과_영수증_주소를_읽는다() {
            stub = new StubHttpServer().stub(CONFIRM_PATH, 200, APPROVAL_BODY);

            PgClient.Approval approval = client().approve("TOSS-KEY", 501L, 32_000L);

            assertThat(approval.paymentKey()).isEqualTo("TOSS-KEY");
            assertThat(approval.method()).isEqualTo("카드");
            // 영수증은 승인 시점에만 받을 수 있어 저장해 둔다.
            assertThat(approval.receiptUrl()).isEqualTo("https://dashboard.tosspayments.com/receipt/x");
        }

        @Test
        void 영수증이_없어도_승인은_성립한다() {
            stub = new StubHttpServer().stub(CONFIRM_PATH, 200, """
                    {"paymentKey":"TOSS-KEY","method":"카드"}""");

            assertThat(client().approve("TOSS-KEY", 501L, 32_000L).receiptUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("거절과 오류 구분")
    class FailureTest {

        @Test
        void 잔액_부족은_402로_옮긴다() {
            // 사용자가 결제 수단을 바꿔 다시 시도할 일이라 서버 오류와 구분해야 한다.
            stub = new StubHttpServer().stub(CONFIRM_PATH, 400, """
                    {"code":"NOT_ENOUGH_BALANCE","message":"잔액이 부족합니다."}""");

            assertThatThrownBy(() -> client().approve("TOSS-KEY", 501L, 32_000L))
                    .isInstanceOf(PaymentDeclinedException.class);
        }

        @Test
        void 한도_초과도_402다() {
            stub = new StubHttpServer().stub(CONFIRM_PATH, 400, """
                    {"code":"EXCEED_MAX_AMOUNT","message":"한도를 초과했습니다."}""");

            assertThatThrownBy(() -> client().approve("TOSS-KEY", 501L, 32_000L))
                    .isInstanceOf(PaymentDeclinedException.class);
        }

        @Test
        void 그_밖의_실패는_서버_오류로_다룬다() {
            // 목록에 없는 코드를 402로 응답하면 고객에게 "잔액이 부족하다"고 잘못 안내하게 된다.
            stub = new StubHttpServer().stub(CONFIRM_PATH, 400, """
                    {"code":"INVALID_REQUEST","message":"잘못된 요청입니다."}""");

            assertThatThrownBy(() -> client().approve("TOSS-KEY", 501L, 32_000L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("결제 처리 중 오류");
        }

        @Test
        void PG_응답_원문을_사용자_응답에_싣지_않는다() {
            stub = new StubHttpServer().stub(CONFIRM_PATH, 500, """
                    {"code":"INTERNAL","message":"내부 시스템 details=db-host-3"}""");

            assertThatThrownBy(() -> client().approve("TOSS-KEY", 501L, 32_000L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageNotContaining("db-host-3");
        }
    }

    @Nested
    @DisplayName("취소")
    class CancelTest {

        @Test
        void 취소_식별자를_돌려준다() {
            stub = new StubHttpServer().stub(CANCEL_PATH, 200, """
                    {"lastTransactionKey":"CANCEL-TX-1","status":"CANCELED"}""");

            PgClient.Cancellation cancellation = client().cancel("TOSS-KEY", 32_000L, "USER_CANCEL");

            assertThat(cancellation.pgCancelKey()).isEqualTo("CANCEL-TX-1");
        }

        @Test
        void 멱등키를_실어_재시도가_이중_취소가_되지_않게_한다() {
            // 재시도 배치가 같은 취소를 여러 번 부를 수 있다.
            stub = new StubHttpServer().stub(CANCEL_PATH, 200, """
                    {"lastTransactionKey":"CANCEL-TX-1"}""");

            client().cancel("TOSS-KEY", 32_000L, "USER_CANCEL");

            assertThat(stub.received(CANCEL_PATH).idempotencyKey()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("설정 검증")
    class PropertiesTest {

        @Test
        void 시크릿_키가_없으면_기동을_막는다() {
            assertThatThrownBy(() -> new TossPaymentProperties(null, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("toss.secret-key가 필요합니다");
        }

        @Test
        void 주입되지_않은_플레이스홀더는_값으로_보지_않는다() {
            assertThatThrownBy(() -> new TossPaymentProperties(null, "${TOSS_SECRET_KEY}"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("환경변수가 주입되지 않았습니다");
        }

        @Test
        void base_url을_지정하지_않으면_토스_기본_주소를_쓴다() {
            assertThat(new TossPaymentProperties(null, SECRET_KEY).baseUrl())
                    .isEqualTo("https://api.tosspayments.com");
        }
    }
}
