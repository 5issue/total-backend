package com.kurly.payment.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentMaintenanceServiceUnitTest {

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);
    private static final Duration RETENTION = Duration.ofDays(7);

    @Mock PaymentRecordService paymentRecordService;
    @InjectMocks PaymentMaintenanceService paymentMaintenanceService;

    private static ArgumentCaptor<LocalDateTime> timeCaptor() {
        return ArgumentCaptor.forClass(LocalDateTime.class);
    }

    @Nested
    @DisplayName("매달린 멱등키 선점 해제")
    class ReleaseStaleTest {

        @Test
        void 유예_시간을_뺀_시각을_기준으로_지운다() {
            given(paymentRecordService.deleteStaleIdempotencyKeys(any(), anyInt())).willReturn(3);
            LocalDateTime before = LocalDateTime.now().minus(STALE_AFTER);

            paymentMaintenanceService.releaseStaleIdempotencyKeys(200, STALE_AFTER);

            ArgumentCaptor<LocalDateTime> captor = timeCaptor();
            verify(paymentRecordService).deleteStaleIdempotencyKeys(captor.capture(), eq(200));
            assertThat(captor.getValue()).isAfterOrEqualTo(before);
        }

        @Test
        void 보관_만료_정리와_섞지_않는다() {
            // 선점 해제는 잠금을 푸는 일이고, 보관 정리는 소임을 다한 기록을 지우는 일이다.
            given(paymentRecordService.deleteStaleIdempotencyKeys(any(), anyInt())).willReturn(0);

            paymentMaintenanceService.releaseStaleIdempotencyKeys(200, STALE_AFTER);

            verify(paymentRecordService, never()).deleteCompletedIdempotencyKeys(any(), anyInt());
        }
    }

    @Nested
    @DisplayName("보관 기간 정리")
    class PurgeTest {

        @Test
        void 보관_기간을_뺀_시각을_기준으로_완료_멱등키를_지운다() {
            given(paymentRecordService.deleteCompletedIdempotencyKeys(any(), anyInt())).willReturn(120);
            LocalDateTime before = LocalDateTime.now().minus(RETENTION);

            paymentMaintenanceService.purgeExpiredIdempotencyKeys(500, RETENTION);

            ArgumentCaptor<LocalDateTime> captor = timeCaptor();
            verify(paymentRecordService).deleteCompletedIdempotencyKeys(captor.capture(), eq(500));
            assertThat(captor.getValue()).isAfterOrEqualTo(before);
        }

        @Test
        void 완료_멱등키_정리가_매달린_선점을_건드리지_않는다() {
            // IN_PROGRESS를 보관 기준으로 지우면 아직 결론이 안 난 결제의 잠금을 성급히 푼다.
            given(paymentRecordService.deleteCompletedIdempotencyKeys(any(), anyInt())).willReturn(0);

            paymentMaintenanceService.purgeExpiredIdempotencyKeys(500, RETENTION);

            verify(paymentRecordService, never()).deleteStaleIdempotencyKeys(any(), anyInt());
        }

        @Test
        void 묶음을_다_채우면_남은_것이_없을_때까지_반복한다() {
            // 한 주기에 한 묶음만 지우면 쌓이는 속도를 따라잡지 못한다.
            given(paymentRecordService.deleteCompletedIdempotencyKeys(any(), eq(500)))
                    .willReturn(500, 500, 120);

            paymentMaintenanceService.purgeExpiredIdempotencyKeys(500, RETENTION);

            verify(paymentRecordService, times(3)).deleteCompletedIdempotencyKeys(any(), eq(500));
        }

        @Test
        void 묶음을_못_채우면_더_돌지_않는다() {
            given(paymentRecordService.deleteCompletedIdempotencyKeys(any(), eq(500))).willReturn(3);

            paymentMaintenanceService.purgeExpiredIdempotencyKeys(500, RETENTION);

            verify(paymentRecordService, times(1)).deleteCompletedIdempotencyKeys(any(), eq(500));
        }

        @Test
        void 끝없이_돌지_않도록_상한에서_멈춘다() {
            // 상한이 없으면 한 주기가 테이블을 오래 붙잡아 다른 워커의 커넥션까지 마른다.
            given(paymentRecordService.deletePublishedOutbox(any(), eq(500))).willReturn(500);

            paymentMaintenanceService.purgePublishedOutbox(500, RETENTION);

            verify(paymentRecordService, atMost(30)).deletePublishedOutbox(any(), eq(500));
        }

        @Test
        void 보관_기간을_뺀_시각을_기준으로_발행_완료_아웃박스를_지운다() {
            given(paymentRecordService.deletePublishedOutbox(any(), anyInt())).willReturn(80);
            LocalDateTime before = LocalDateTime.now().minus(RETENTION);

            paymentMaintenanceService.purgePublishedOutbox(500, RETENTION);

            ArgumentCaptor<LocalDateTime> captor = timeCaptor();
            verify(paymentRecordService).deletePublishedOutbox(captor.capture(), eq(500));
            assertThat(captor.getValue()).isAfterOrEqualTo(before);
        }
    }
}
