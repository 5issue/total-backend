package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.repository.PaymentRepository;
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
import static com.kurly.payment.application.PaymentFixtures.approvedPayment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceUnitTest {

    @Mock PaymentRepository paymentRepository;
    @InjectMocks PaymentQueryService paymentQueryService;

    @Nested
    @DisplayName("영수증 조회")
    class ReceiptTest {

        @Test
        void 본인_결제는_조회된다() {
            Payment payment = approvedPayment(10L);
            given(paymentRepository.findByIdAndUserId(10L, USER_ID)).willReturn(Optional.of(payment));

            assertThat(paymentQueryService.getOwnedPayment(10L, USER_ID))
                    .isSameAs(payment)
                    .extracting(Payment::getReceiptUrl)
                    .isEqualTo("https://toss.im/receipt/x");
        }

        @Test
        void 조회는_소유자_조건을_함께_건다() {
            // 애플리케이션에서 걸러내지 않고 조회 조건에 넣어야 타인 결제가 결과에 잡히지 않는다.
            given(paymentRepository.findByIdAndUserId(10L, 999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> paymentQueryService.getOwnedPayment(10L, 999L))
                    .isInstanceOf(PaymentNotFoundException.class);
        }
    }
}
