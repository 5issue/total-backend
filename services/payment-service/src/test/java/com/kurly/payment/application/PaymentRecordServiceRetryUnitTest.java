package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.enums.PaymentStatus;
import com.kurly.payment.domain.enums.RetryStatus;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.domain.repository.PaymentRetryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.kurly.payment.application.PaymentFixtures.AMOUNT;
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static com.kurly.payment.application.PaymentFixtures.cancel;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 재시도 배치가 쓰는 기록 동작. */
@ExtendWith(MockitoExtension.class)
class PaymentRecordServiceRetryUnitTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCancelRepository paymentCancelRepository;
    @Mock PaymentOutboxRepository paymentOutboxRepository;
    @Mock PaymentRetryRepository paymentRetryRepository;

    PaymentRecordService paymentRecordService;

    @BeforeEach
    void setUp() {
        paymentRecordService = new PaymentRecordService(paymentRepository, paymentCancelRepository,
                paymentOutboxRepository, paymentRetryRepository, JsonMapper.builder().build());
    }

    private static PaymentRetry retry(Long id, Payment payment, Long cancelId) {
        PaymentRetry retry = PaymentRetry.builder()
                .payment(payment)
                .taskType(PaymentRecordService.PG_CANCEL_TASK)
                .payload("{\"paymentCancelId\":%d,\"cancelAmount\":%d}".formatted(cancelId, AMOUNT))
                .build();
        ReflectionTestUtils.setField(retry, "id", id);
        return retry;
    }

    @Nested
    @DisplayName("선점")
    class ClaimTest {

        @Test
        void PG_호출에_필요한_값을_값으로_뽑아_돌려준다() {
            // 엔티티를 그대로 넘기면 트랜잭션이 끝난 뒤 지연 로딩된 payment에 접근하다 실패한다.
            Payment payment = approvedPayment(10L);
            given(paymentRetryRepository.findDueForUpdateSkipLocked(any(), anyInt()))
                    .willReturn(List.of(retry(30L, payment, 20L)));

            List<PaymentRecordService.RetryTask> tasks =
                    paymentRecordService.claimDueRetries(50, Duration.ofMinutes(5));

            assertThat(tasks).singleElement()
                    .extracting(PaymentRecordService.RetryTask::retryId,
                            PaymentRecordService.RetryTask::paymentKey,
                            PaymentRecordService.RetryTask::paymentCancelId,
                            PaymentRecordService.RetryTask::cancelAmount)
                    .containsExactly(30L, "TOSS-KEY", 20L, AMOUNT);
        }

        @Test
        void 선점하면_예정_시각을_뒤로_민다() {
            // PG 호출은 트랜잭션 밖이라 행 잠금이 풀린다. 임대 시각이 중복 실행을 막는다.
            Payment payment = approvedPayment(10L);
            PaymentRetry target = retry(30L, payment, 20L);
            given(paymentRetryRepository.findDueForUpdateSkipLocked(any(), anyInt()))
                    .willReturn(List.of(target));

            paymentRecordService.claimDueRetries(50, Duration.ofMinutes(5));

            assertThat(target.getNextRetryAt()).isAfter(LocalDateTime.now().plusMinutes(4));
            // 아직 실패한 것이 아니므로 시도 횟수는 그대로다.
            assertThat(target.getRetryCount()).isZero();
        }
    }

    @Nested
    @DisplayName("재시도 완료")
    class CompleteTest {

        @Test
        void 취소를_확정하고_재시도를_SUCCESS로_끝낸다() {
            Payment payment = approvedPayment(10L);
            PaymentCancel target = cancel(20L, payment, AMOUNT);
            PaymentRetry retryRow = retry(30L, payment, 20L);
            given(paymentCancelRepository.findById(20L)).willReturn(Optional.of(target));
            given(paymentRetryRepository.findById(30L)).willReturn(Optional.of(retryRow));

            paymentRecordService.completeRetry(30L, 20L, "PG-CANCEL-9");

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(retryRow.getStatus()).isEqualTo(RetryStatus.SUCCESS);
            assertThat(retryRow.getNextRetryAt()).isNull();
            verify(paymentOutboxRepository).save(any());
        }
    }

    @Nested
    @DisplayName("재시도 실패")
    class FailTest {

        @Test
        void 기존_행에_기록하고_새_행을_만들지_않는다() {
            // 새로 만들면 시도할 때마다 큐가 불어난다.
            PaymentRetry retryRow = retry(30L, approvedPayment(10L), 20L);
            given(paymentRetryRepository.findById(30L)).willReturn(Optional.of(retryRow));

            paymentRecordService.failRetry(30L, "PG timeout");

            assertThat(retryRow.getRetryCount()).isEqualTo(1);
            assertThat(retryRow.getLastError()).isEqualTo("PG timeout");
            verify(paymentRetryRepository, never()).save(any());
        }

        @Test
        void 상한에_도달하면_FAILED로_멈춘다() {
            PaymentRetry retryRow = retry(30L, approvedPayment(10L), 20L);
            given(paymentRetryRepository.findById(30L)).willReturn(Optional.of(retryRow));

            for (int i = 0; i < PaymentRecordService.MAX_RETRY_COUNT; i++) {
                paymentRecordService.failRetry(30L, "PG timeout");
            }

            assertThat(retryRow.getStatus()).isEqualTo(RetryStatus.FAILED);
            assertThat(retryRow.getNextRetryAt()).isNull();
        }
    }
}
