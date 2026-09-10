package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.cancel;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCompensationServiceUnitTest {

    private static final Long PAYMENT_ID = 10L;
    private static final Long CANCEL_ID = 20L;

    @Mock PgClient pgClient;
    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentCompensationService paymentCompensationService;

    private void givenCancelBegun() {
        given(paymentRecordService.beginCancel(eq(PAYMENT_ID), anyString(), eq(AMOUNT)))
                .willReturn(cancel(CANCEL_ID, approvedPayment(PAYMENT_ID), AMOUNT));
    }

    @Nested
    @DisplayName("보상 취소")
    class CompensateTest {

        @Test
        void PG_취소가_성공하면_취소를_확정한다() {
            givenCancelBegun();
            given(pgClient.cancel(anyString(), eq(AMOUNT), anyString(), anyLong()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-1"));

            paymentCompensationService.compensate(PAYMENT_ID, "TOSS-KEY", AMOUNT, "ORDER_EXPIRED");

            verify(paymentRecordService).completeCancel(CANCEL_ID, "PG-CANCEL-1");
        }

        @Test
        void 인자로_받은_키로_PG에_취소를_건다() {
            // 중복 결제로 기록이 롤백된 경우 엔티티에는 키가 없다. 승인 응답의 키만이 쓸 수 있다.
            givenCancelBegun();
            given(pgClient.cancel(anyString(), eq(AMOUNT), anyString(), anyLong()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-1"));

            paymentCompensationService.compensate(PAYMENT_ID, "PG-ISSUED-KEY", AMOUNT, "DUPLICATE_PAYMENT");

            verify(pgClient).cancel(eq("PG-ISSUED-KEY"), eq(AMOUNT), eq("DUPLICATE_PAYMENT"), eq(CANCEL_ID));
        }

        @Test
        void 취소_시도는_PG_호출_전에_먼저_남는다() {
            // 시도 기록이 없으면 PG 호출이 실패했을 때 재시도할 근거가 사라진다.
            givenCancelBegun();
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), eq(AMOUNT), anyString(), anyLong());

            paymentCompensationService.compensate(PAYMENT_ID, "TOSS-KEY", AMOUNT, "ORDER_EXPIRED");

            verify(paymentRecordService).beginCancel(eq(PAYMENT_ID), eq("ORDER_EXPIRED"), eq(AMOUNT));
        }
    }

    @Nested
    @DisplayName("보상 취소 실패")
    class CompensateFailureTest {

        @Test
        void PG_취소가_실패하면_재시도_큐로_넘긴다() {
            // 여기서 멈추면 고객 돈이 묶인 채로 잊힌다.
            givenCancelBegun();
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), eq(AMOUNT), anyString(), anyLong());

            paymentCompensationService.compensate(PAYMENT_ID, "TOSS-KEY", AMOUNT, "ORDER_EXPIRED");

            verify(paymentRecordService).failCancel(eq(CANCEL_ID), anyString());
            verify(paymentRecordService, never()).completeCancel(eq(CANCEL_ID), anyString());
        }

        @Test
        void 실패해도_예외를_밖으로_내보내지_않는다() {
            // 호출부는 자기 흐름의 결론을 따로 정한다. 여기서 예외가 나가면 그 결론을 덮어쓴다.
            givenCancelBegun();
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), eq(AMOUNT), anyString(), anyLong());

            assertThatCode(() ->
                    paymentCompensationService.compensate(PAYMENT_ID, "TOSS-KEY", AMOUNT, "ORDER_EXPIRED"))
                    .doesNotThrowAnyException();
        }
    }
}
