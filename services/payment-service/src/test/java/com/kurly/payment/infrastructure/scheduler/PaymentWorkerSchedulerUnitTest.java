package com.kurly.payment.infrastructure.scheduler;

import com.kurly.payment.application.OutboxPublishService;
import com.kurly.payment.application.PaymentRetryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerSchedulerUnitTest {

    @Mock OutboxPublishService outboxPublishService;
    @Mock PaymentRetryService paymentRetryService;
    @InjectMocks PaymentWorkerScheduler scheduler;

    private void givenBatchSizes() {
        ReflectionTestUtils.setField(scheduler, "outboxBatchSize", 100);
        ReflectionTestUtils.setField(scheduler, "retryBatchSize", 50);
    }

    @Nested
    @DisplayName("주기 실행")
    class RunTest {

        @Test
        void 설정한_묶음_크기로_아웃박스를_발행한다() {
            givenBatchSizes();
            given(outboxPublishService.publishPending(100)).willReturn(3);

            scheduler.publishOutbox();

            verify(outboxPublishService).publishPending(100);
        }

        @Test
        void 설정한_묶음_크기로_재시도를_실행한다() {
            givenBatchSizes();
            given(paymentRetryService.runDueTasks(50)).willReturn(1);

            scheduler.runRetries();

            verify(paymentRetryService).runDueTasks(50);
        }
    }

    @Nested
    @DisplayName("예외 격리")
    class IsolationTest {

        @Test
        void 아웃박스_발행이_실패해도_예외를_밖으로_내보내지_않는다() {
            // 스케줄러 밖으로 나가면 로그가 프레임워크 형식으로만 남아 원인을 찾기 어렵다.
            givenBatchSizes();
            willThrow(new IllegalStateException("boom")).given(outboxPublishService).publishPending(anyInt());

            assertThatCode(() -> scheduler.publishOutbox()).doesNotThrowAnyException();
        }

        @Test
        void 재시도_배치가_실패해도_예외를_밖으로_내보내지_않는다() {
            givenBatchSizes();
            willThrow(new IllegalStateException("boom")).given(paymentRetryService).runDueTasks(anyInt());

            assertThatCode(() -> scheduler.runRetries()).doesNotThrowAnyException();
        }
    }
}
