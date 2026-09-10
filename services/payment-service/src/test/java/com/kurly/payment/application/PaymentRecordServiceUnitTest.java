package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.domain.repository.PaymentRetryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.cancel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceUnitTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancelRepository paymentCancelRepository;
    @Mock PaymentOutboxRepository paymentOutboxRepository;
    @Mock PaymentRetryRepository paymentRetryRepository;

    PaymentRecordService paymentRecordService;

    private void createService() {
        paymentRecordService = new PaymentRecordService(paymentRepository, paymentCancelRepository,
                paymentOutboxRepository, paymentRetryRepository, JsonMapper.builder().build());
    }

    @Nested
    @DisplayName("승인 기록")
    class ApprovalTest {

        @Test
        void 승인_응답의_식별자_수단_영수증을_모두_남긴다() {
            createService();
            Payment payment = PaymentFixtures.payment(10L);
            given(paymentRepository.findById(10L)).willReturn(Optional.of(payment));

            Payment result = paymentRecordService.recordApproval(10L,
                    new PgClient.Approval("TOSS-KEY", "CARD", "https://toss.im/r/1"));

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(result.getPaymentKey()).isEqualTo("TOSS-KEY");
            assertThat(result.getMethod()).isEqualTo("CARD");
            // 조회 때마다 PG에 묻지 않으려면 승인 시점에 받아 저장해야 한다.
            assertThat(result.getReceiptUrl()).isEqualTo("https://toss.im/r/1");
            assertThat(result.getApprovedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("취소 완료 기록")
    class CancelCompletionTest {

        private PaymentCancel givenCancel(Payment payment, long cancelAmount) {
            createService();
            PaymentCancel target = cancel(20L, payment, cancelAmount);
            given(paymentCancelRepository.findById(20L)).willReturn(Optional.of(target));
            return target;
        }

        @Test
        void 취소_성공은_전액_취소다() {
            // 부분 취소를 제공하지 않으므로 취소 성공은 곧 CANCELED다.
            Payment payment = approvedPayment(10L);
            givenCancel(payment, AMOUNT);

            paymentRecordService.completeCancel(20L, "PG-CANCEL-1");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(payment.getCanceledAt()).isNotNull();
        }

        @Test
        void 이벤트를_아웃박스에_적재한다() {
            Payment payment = approvedPayment(10L);
            givenCancel(payment, AMOUNT);

            paymentRecordService.completeCancel(20L, "PG-CANCEL-1");

            ArgumentCaptor<PaymentOutbox> captor = ArgumentCaptor.forClass(PaymentOutbox.class);
            verify(paymentOutboxRepository).save(captor.capture());
            PaymentOutbox outbox = captor.getValue();
            assertThat(outbox.getEventType()).isEqualTo(PaymentRecordService.PAYMENT_CANCELED_EVENT);
            assertThat(outbox.getEventId()).hasSize(36);
            // JWT 원문은 싣지 않는다. 큐는 영속화되고 재시도로 오래 남는다.
            assertThat(outbox.getPayload()).contains("\"userId\":1").doesNotContain("eyJ");
        }
    }

    @Nested
    @DisplayName("취소 실패 기록")
    class CancelFailureTest {

        @Test
        void 이력에_사유를_남기고_재시도_큐에_넣는다() {
            createService();
            Payment payment = approvedPayment(10L);
            PaymentCancel target = cancel(20L, payment, AMOUNT);
            given(paymentCancelRepository.findById(20L)).willReturn(Optional.of(target));

            paymentRecordService.failCancel(20L, "PG timeout");

            assertThat(target.getFailureReason()).isEqualTo("PG timeout");
            ArgumentCaptor<PaymentRetry> captor = ArgumentCaptor.forClass(PaymentRetry.class);
            verify(paymentRetryRepository).save(captor.capture());
            assertThat(captor.getValue().getTaskType()).isEqualTo(PaymentRecordService.PG_CANCEL_TASK);
            assertThat(captor.getValue().getNextRetryAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("결제 요청 생성")
    class CreateTest {

        @Test
        void REQUESTED_상태로_먼저_남긴다() {
            createService();
            given(paymentRepository.save(any())).willAnswer(i -> i.getArgument(0));

            Payment created = paymentRecordService.createRequested(111L, 1L, AMOUNT);

            assertThat(created.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
            assertThat(created.getPaymentKey()).isNull();
        }
    }
}
