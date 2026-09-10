package com.kurly.payment.application;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.exception.AmountMismatchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IdempotentRequestExecutorUnitTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "uuid-1";
    private static final String PATH = "/api/v1/payments/checkout";

    record Request(Long orderId, Long amount) {
    }

    record Response(String status) {
    }

    @Mock IdempotencyService idempotencyService;

    IdempotentRequestExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new IdempotentRequestExecutor(idempotencyService, JsonMapper.builder().build());
    }

    private static IdempotencyKey record(Long id, boolean completed) {
        IdempotencyKey key = IdempotencyKey.builder()
                .userId(USER_ID).idempotencyKey(KEY).requestPath(PATH)
                .requestFingerprint("f".repeat(64)).build();
        ReflectionTestUtils.setField(key, "id", id);
        if (completed) {
            key.complete(200, "{\"status\":\"SUCCESS\"}");
        }
        return key;
    }

    @Nested
    @DisplayName("선점 성공")
    class ProceedTest {

        @Test
        void 처리를_실행하고_응답을_저장한다() {
            given(idempotencyService.begin(eq(USER_ID), eq(KEY), eq(PATH), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, false), false));

            var outcome = executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> new Response("SUCCESS"));

            assertThat(outcome.replayed()).isFalse();
            assertThat(outcome.body().status()).isEqualTo("SUCCESS");
            verify(idempotencyService).complete(eq(5L), eq(200), anyString());
        }

        @Test
        void 본문은_지문으로_넘긴다() {
            // 원문을 넘기면 JSON 컬럼 정규화 때문에 재요청이 항상 "다른 본문"으로 판정된다.
            given(idempotencyService.begin(anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, false), false));

            executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> new Response("SUCCESS"));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyService).begin(anyLong(), anyString(), anyString(), captor.capture());
            assertThat(captor.getValue()).hasSize(64).matches("[0-9a-f]{64}");
        }

        @Test
        void 같은_본문은_같은_지문을_만든다() {
            given(idempotencyService.begin(anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, false), false));

            executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> new Response("SUCCESS"));
            executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> new Response("SUCCESS"));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyService, org.mockito.Mockito.times(2))
                    .begin(anyLong(), anyString(), anyString(), captor.capture());
            assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
        }
    }

    @Nested
    @DisplayName("재생")
    class ReplayTest {

        @Test
        void 저장된_응답을_돌려주고_처리를_실행하지_않는다() {
            given(idempotencyService.begin(anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, true), true));

            var outcome = executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> {
                        throw new AssertionError("재생일 때 처리를 실행하면 이중 결제가 된다");
                    });

            assertThat(outcome.replayed()).isTrue();
            assertThat(outcome.status()).isEqualTo(200);
            assertThat(outcome.body().status()).isEqualTo("SUCCESS");
            verify(idempotencyService, never()).complete(anyLong(), anyInt(), anyString());
        }
    }

    @Nested
    @DisplayName("실패 처리")
    class FailureTest {

        @Test
        void 업무_예외는_선점을_풀어_재시도를_허용한다() {
            // 금액 불일치·상태 오류·PG 거절은 돈이 움직이지 않은 것이 확실하다.
            given(idempotencyService.begin(anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, false), false));

            assertThatThrownBy(() -> executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> {
                        throw new AmountMismatchException();
                    })).isInstanceOf(AmountMismatchException.class);

            verify(idempotencyService).release(5L);
            verify(idempotencyService, never()).complete(anyLong(), anyInt(), anyString());
        }

        @Test
        void 알_수_없는_실패는_선점을_유지해_재시도를_막는다() {
            // PG 타임아웃은 승인 여부를 알 수 없다. 풀어주면 재시도가 이중 결제가 된다.
            given(idempotencyService.begin(anyLong(), anyString(), anyString(), anyString()))
                    .willReturn(new IdempotencyService.Result(record(5L, false), false));

            assertThatThrownBy(() -> executor.execute(USER_ID, KEY, PATH, new Request(111L, 32_000L),
                    Response.class, 200, () -> {
                        throw new IllegalStateException("PG timeout");
                    })).isInstanceOf(IllegalStateException.class);

            verify(idempotencyService, never()).release(anyLong());
            verify(idempotencyService, never()).complete(anyLong(), anyInt(), anyString());
        }
    }
}
