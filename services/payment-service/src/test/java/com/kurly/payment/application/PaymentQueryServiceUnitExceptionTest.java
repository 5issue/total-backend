package com.kurly.payment.application;

import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.PaymentErrorCode;
import com.kurly.payment.exception.PaymentNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.kurly.payment.application.PaymentFixtures.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceUnitExceptionTest {

    @Mock PaymentRepository paymentRepository;
    @InjectMocks PaymentQueryService paymentQueryService;

    @Nested
    @DisplayName("조회 실패")
    class NotFoundTest {

        @Test
        void 없는_결제와_타인의_결제는_같은_예외다() {
            // 구분해 응답하면 ID를 훑어 남의 결제 존재 여부를 알아낼 수 있다.
            given(paymentRepository.findByIdAndUserId(99999L, USER_ID)).willReturn(Optional.empty());
            given(paymentRepository.findByIdAndUserId(10L, 999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentQueryService.getOwnedPayment(99999L, USER_ID))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessage(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage());
            assertThatThrownBy(() -> paymentQueryService.getOwnedPayment(10L, 999L))
                    .isInstanceOf(PaymentNotFoundException.class)
                    .hasMessage(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("에러 코드")
    class ErrorCodeTest {

        @Test
        void 결제_없음은_404다() {
            assertThat(PaymentErrorCode.PAYMENT_NOT_FOUND.getStatus().value()).isEqualTo(404);
        }

        @Test
        void PG_거절은_402다() {
            // 사용자가 결제 수단을 바꿔 재시도해야 하는 상황이라 400과 구분한다.
            assertThat(PaymentErrorCode.PAYMENT_REQUIRED.getStatus().value()).isEqualTo(402);
        }

        @Test
        void 금액_불일치는_전용_코드를_쓴다() {
            // 위변조는 별도로 추적·경보해야 해서 일반 입력 오류와 코드를 나눈다.
            assertThat(PaymentErrorCode.AMOUNT_MISMATCH.getCode())
                    .isNotEqualTo(PaymentErrorCode.INVALID_PAYMENT_STATUS.getCode());
        }
    }
}
