package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
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
import static com.kurly.payment.application.PaymentFixtures.payment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCancelServiceUnitExceptionTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PgClient pgClient;
    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentCancelService paymentCancelService;

    @Nested
    @DisplayName("대상 검증 실패")
    class TargetTest {

        @Test
        void 없는_결제는_404다() {
            given(paymentRepository.findById(99999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCancelService.cancel(99999L, USER_ID, "USER_CANCEL"))
                    .isInstanceOf(PaymentNotFoundException.class);
        }

        @Test
        void 타인의_결제도_같은_404다() {
            // 403으로 구분하면 ID를 훑어 남의 결제 존재 여부를 알아낼 수 있다.
            given(paymentRepository.findById(10L)).willReturn(Optional.of(approvedPayment(10L)));

            assertThatThrownBy(() -> paymentCancelService.cancel(10L, 999L, "USER_CANCEL"))
                    .isInstanceOf(PaymentNotFoundException.class);
            verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), anyLong());
        }

        @Test
        void 승인되지_않은_결제는_취소할_수_없다() {
            // REQUESTED 상태는 PG에 승인된 적이 없어 취소할 대상이 없다.
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment(10L)));

            assertThatThrownBy(() -> paymentCancelService.cancel(10L, USER_ID, "USER_CANCEL"))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        void 이미_취소된_결제는_다시_취소할_수_없다() {
            Payment canceled = approvedPayment(10L);
            canceled.cancel();
            given(paymentRepository.findById(10L)).willReturn(Optional.of(canceled));

            assertThatThrownBy(() -> paymentCancelService.cancel(10L, USER_ID, "USER_CANCEL"))
                    .isInstanceOf(InvalidPaymentStatusException.class);
        }

        @Test
        void 검증에_실패하면_취소_이력을_남기지_않는다() {
            given(paymentRepository.findById(99999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentCancelService.cancel(99999L, USER_ID, "USER_CANCEL"))
                    .isInstanceOf(PaymentNotFoundException.class);
            verify(paymentRecordService, never()).beginCancel(any(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("PG 취소 실패")
    class PgFailureTest {

        @Test
        void 이력에_남기고_재시도_큐로_넘긴_뒤_예외를_전파한다() {
            // 조용히 삼키면 고객 돈이 묶인 채 잊힌다.
            Payment payment = approvedPayment(10L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));
            given(paymentRecordService.beginCancel(eq(10L), anyString(), eq(AMOUNT)))
                    .willReturn(cancel(20L, payment, AMOUNT));
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), eq(AMOUNT), anyString(), anyLong());

            assertThatThrownBy(() -> paymentCancelService.cancel(10L, USER_ID, "USER_CANCEL"))
                    .isInstanceOf(IllegalStateException.class);
            verify(paymentRecordService).failCancel(eq(20L), anyString());
            verify(paymentRecordService, never()).completeCancel(anyLong(), anyString());
        }
    }
}
