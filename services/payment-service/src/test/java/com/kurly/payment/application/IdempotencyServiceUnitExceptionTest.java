package com.kurly.payment.application;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import com.kurly.payment.exception.DuplicatePaymentRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceUnitExceptionTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "0f7b2a1e-uuid";
    private static final String PATH = "/api/v1/payments/checkout";
    private static final String FINGERPRINT = "a".repeat(64);

    @Mock IdempotencyKeyRepository idempotencyKeyRepository;
    @InjectMocks IdempotencyService idempotencyService;

    private static IdempotencyKey inProgress(String path, String body) {
        return IdempotencyKey.builder()
                .userId(USER_ID).idempotencyKey(KEY).requestPath(path).requestFingerprint(body).build();
    }

    @Nested
    @DisplayName("중복 요청 차단")
    class DuplicateTest {

        @Test
        void 처리_중인_키는_409다() {
            // 결과를 알 수 없으므로 새로 처리하면 이중 결제 위험이 있다.
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.of(inProgress(PATH, FINGERPRINT)));

            assertThatThrownBy(() -> idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT))
                    .isInstanceOf(DuplicatePaymentRequestException.class);
        }

        @Test
        void 같은_키를_다른_엔드포인트에_쓰면_409다() {
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.of(inProgress("/api/v1/payments/1/cancel", FINGERPRINT)));

            assertThatThrownBy(() -> idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT))
                    .isInstanceOf(DuplicatePaymentRequestException.class);
        }

        @Test
        void 같은_키에_다른_본문이_오면_409다() {
            // 응답을 재생하면 클라이언트가 보낸 적 없는 결과를 돌려주게 된다.
            given(idempotencyKeyRepository.findByUserIdAndIdempotencyKey(USER_ID, KEY))
                    .willReturn(Optional.of(inProgress(PATH, "{\"orderId\":999}")));

            assertThatThrownBy(() -> idempotencyService.begin(USER_ID, KEY, PATH, FINGERPRINT))
                    .isInstanceOf(DuplicatePaymentRequestException.class);
        }
    }
}
