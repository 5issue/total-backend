package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRetryServiceUnitTest {

    @Mock PaymentRecordService paymentRecordService;
    @Mock PgClient pgClient;
    @InjectMocks PaymentRetryService paymentRetryService;

    private static PaymentRecordService.RetryTask task(String taskType) {
        return new PaymentRecordService.RetryTask(30L, taskType, "TOSS-KEY", 20L, 32_000L);
    }

    @Nested
    @DisplayName("PG 취소 재시도")
    class CancelRetryTest {

        @Test
        void 성공하면_취소를_확정하고_재시도를_종료한다() {
            given(paymentRecordService.claimDueRetries(eq(50), any()))
                    .willReturn(List.of(task(PaymentRecordService.PG_CANCEL_TASK)));
            given(pgClient.cancel("TOSS-KEY", 32_000L, "RETRY", 20L))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-9"));

            assertThat(paymentRetryService.runDueTasks(50)).isEqualTo(1);

            verify(paymentRecordService).completeRetry(30L, 20L, "PG-CANCEL-9");
            verify(paymentRecordService, never()).failRetry(anyLong(), anyString());
        }

        @Test
        void 또_실패하면_기존_행에_기록한다() {
            // 새 재시도 행을 만들면 시도할 때마다 큐가 불어난다.
            given(paymentRecordService.claimDueRetries(eq(50), any()))
                    .willReturn(List.of(task(PaymentRecordService.PG_CANCEL_TASK)));
            willThrow(new IllegalStateException("PG timeout"))
                    .given(pgClient).cancel(anyString(), anyLong(), anyString(), anyLong());

            assertThat(paymentRetryService.runDueTasks(50)).isZero();

            verify(paymentRecordService).failRetry(eq(30L), anyString());
            verify(paymentRecordService, never()).completeRetry(anyLong(), anyLong(), anyString());
        }

        @Test
        void 한_건이_실패해도_나머지는_계속_처리한다() {
            var failing = new PaymentRecordService.RetryTask(
                    30L, PaymentRecordService.PG_CANCEL_TASK, "KEY-A", 20L, 1_000L);
            var succeeding = new PaymentRecordService.RetryTask(
                    31L, PaymentRecordService.PG_CANCEL_TASK, "KEY-B", 21L, 2_000L);
            given(paymentRecordService.claimDueRetries(eq(50), any()))
                    .willReturn(List.of(failing, succeeding));
            willThrow(new IllegalStateException("boom"))
                    .given(pgClient).cancel(eq("KEY-A"), anyLong(), anyString(), anyLong());
            given(pgClient.cancel(eq("KEY-B"), anyLong(), anyString(), anyLong()))
                    .willReturn(new PgClient.Cancellation("PG-CANCEL-B"));

            assertThat(paymentRetryService.runDueTasks(50)).isEqualTo(1);

            verify(paymentRecordService).failRetry(eq(30L), anyString());
            verify(paymentRecordService).completeRetry(31L, 21L, "PG-CANCEL-B");
        }
    }

    @Nested
    @DisplayName("선점")
    class ClaimTest {

        @Test
        void 처리할_것이_없으면_PG를_호출하지_않는다() {
            given(paymentRecordService.claimDueRetries(anyInt(), any())).willReturn(List.of());

            assertThat(paymentRetryService.runDueTasks(50)).isZero();

            verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), anyLong());
        }

        @Test
        void 선점_시간을_함께_넘긴다() {
            // PG 호출은 트랜잭션 밖에서 하므로 행 잠금이 풀린다. 임대 시각이 중복 실행을 막는다.
            given(paymentRecordService.claimDueRetries(eq(50), eq(PaymentRetryService.LEASE)))
                    .willReturn(List.of());

            paymentRetryService.runDueTasks(50);

            verify(paymentRecordService).claimDueRetries(50, PaymentRetryService.LEASE);
        }
    }

    @Nested
    @DisplayName("알 수 없는 작업")
    class UnknownTaskTest {

        @Test
        void 지원하지_않는_종류는_실패로_기록해_상한에_걸리게_한다() {
            // 그대로 두면 배치가 매 주기 같은 건을 집어가며 헛돈다.
            given(paymentRecordService.claimDueRetries(eq(50), any()))
                    .willReturn(List.of(task("UNKNOWN_TASK")));

            assertThat(paymentRetryService.runDueTasks(50)).isZero();

            verify(paymentRecordService).failRetry(eq(30L), anyString());
            verify(pgClient, never()).cancel(anyString(), anyLong(), anyString(), anyLong());
        }
    }
}
