package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.USER_ID;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.cancel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCancelServiceUnitTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PgClient pgClient;
    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentCancelService paymentCancelService;

    /** 취소 흐름의 공통 스텁. completeCancel은 갱신된 인스턴스를 돌려주는 계약이다. */
    private void givenApprovedPayment(String pgCancelKey) {
        Payment payment = approvedPayment(10L);
        var pending = cancel(20L, payment, AMOUNT);
        given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
        given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT))).willReturn(pending);
        given(pgClient.cancel(anyString(), eq(AMOUNT), anyString()))
                .willReturn(new PgClient.Cancellation(pgCancelKey));
        given(paymentRecordService.completeCancel(20L, pgCancelKey)).willReturn(pending);
    }

    @Nested
    @DisplayName("동기 경로 — 사용자 요청")
    class SyncTest {

        @Test
        void 소유자가_일치하면_취소한다() {
            givenApprovedPayment("PG-CANCEL-1");

            assertThat(paymentCancelService.cancel(10L, USER_ID, "USER_CANCEL").getId()).isEqualTo(20L);
            verify(paymentRecordService).completeCancel(20L, "PG-CANCEL-1");
        }

        @Test
        void PG_호출_전에_취소_시도를_먼저_남긴다() {
            // 실패해도 시도한 사실이 남아야 배치가 재시도할 수 있다.
            givenApprovedPayment("PG-CANCEL-1");

            paymentCancelService.cancel(10L, USER_ID, "USER_CANCEL");

            var inOrder = org.mockito.Mockito.inOrder(paymentRecordService, pgClient);
            inOrder.verify(paymentRecordService).beginCancel(eq(10L), anyString(), eq(AMOUNT));
            inOrder.verify(pgClient).cancel(anyString(), eq(AMOUNT), anyString());
        }
    }

    @Nested
    @DisplayName("비동기 경로 — 관리자 승인 환불")
    class AsyncTest {

        @Test
        void 소유자_식별자가_없으면_대조를_건너뛴다() {
            // 반품 승인 환불의 행위자는 관리자다. 대조할 사용자 토큰이 존재하지 않는다.
            givenApprovedPayment("PG-CANCEL-2");

            paymentCancelService.cancel(10L, null, "RETURN_APPROVED");

            verify(paymentRecordService).completeCancel(20L, "PG-CANCEL-2");
        }
    }
}
