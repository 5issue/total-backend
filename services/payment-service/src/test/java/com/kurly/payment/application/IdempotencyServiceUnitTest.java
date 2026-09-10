package com.kurly.payment.application;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceUnitTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "0f7b2a1e-uuid";
    private static final String PATH = "/api/v1/payments/checkout";
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @InjectMocks IdempotencyService idempotencyService;

    private static IdempotencyKey record(Long id, boolean completed) {
        IdempotencyKey key = IdempotencyKey.builder()
                .userId(USER_ID).idempotencyKey(KEY).requestPath(PATH).requestFingerprint(FINGERPRINT).build();
        ReflectionTestUtils.setField(key, "id", id);
        if (completed) {
            key.complete(200, "{\"paymentStatus\":\"SUCCESS\"}");
        }
        return key;
    }

    @Nested
    @DisplayName("선점")
    class BeginTest {

        @Test
        void 처음_보는_키는_선점하고_처리를_진행한다() {
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.empty());
            given(idempotencyKeyRepository.save(any())).willAnswer(i -> i.getArgument(0));

            IdempotencyService.Result result = idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT);

            assertThat(result.replay()).isFalse();
            assertThat(result.record().isCompleted()).isFalse();
        }

        @Test
        void 완료된_키는_저장된_응답을_재생한다() {
            // 결제를 다시 시도하면 이중 결제가 된다.
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.of(record(5L, true)));

            IdempotencyService.Result result = idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT);

            assertThat(result.replay()).isTrue();
            assertThat(result.record().getResponseStatus()).isEqualTo(200);
        }

        @Test
        void 조회와_저장_사이에_끼어든_요청은_유니크_제약이_가른다() {
            // 애플리케이션 조회만으로는 동시 요청을 막지 못한다.
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.empty())
                    .willReturn(Optional.of(record(5L, true)));
            given(idempotencyKeyRepository.save(any()))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            IdempotencyService.Result result = idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT);

            assertThat(result.replay()).isTrue();
        }
    }

    @Nested
    @DisplayName("완료 기록")
    class CompleteTest {

        @Test
        void 응답을_저장하면_다음_요청부터_재생된다() {
            IdempotencyKey key = record(5L, false);
            given(idempotencyKeyRepository.findById(5L)).willReturn(Optional.of(key));

            idempotencyService.complete(5L, 201, "{\"paymentId\":1}");

            assertThat(key.isCompleted()).isTrue();
            assertThat(key.getResponseStatus()).isEqualTo(201);
            assertThat(key.getResponseBody()).isEqualTo("{\"paymentId\":1}");
        }

        @Test
        void 기록이_사라졌어도_예외를_던지지_않는다() {
            // 완료 기록 실패가 이미 성공한 결제를 되돌리게 두면 안 된다.
            given(idempotencyKeyRepository.findById(5L)).willReturn(Optional.empty());

            idempotencyService.complete(5L, 200, "{}");
        }
    }
}
