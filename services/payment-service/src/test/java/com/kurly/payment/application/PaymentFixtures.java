package com.kurly.payment.application;

import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import org.springframework.test.util.ReflectionTestUtils;

/** 테스트에서 반복되는 엔티티 조립. id는 DB가 채우므로 리플렉션으로 심는다. */
final class PaymentFixtures {

    static final Long USER_ID = 1L;
    static final Long ORDER_ID = 111L;
    static final long AMOUNT = 32_000L;

    private PaymentFixtures() {
    }

    static Payment payment(Long id) {
        Payment payment = Payment.builder()
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .totalAmount(AMOUNT)
                .build();
        ReflectionTestUtils.setField(payment, "id", id);
        return payment;
    }

    /** 승인까지 마친 결제. 취소 대상이 되려면 SUCCESS여야 한다. */
    static Payment approvedPayment(Long id) {
        Payment payment = payment(id);
        payment.approve("TOSS-KEY", "CARD", "https://toss.im/receipt/x");
        return payment;
    }

    static PaymentCancel cancel(Long id, Payment payment, long amount) {
        PaymentCancel cancel = PaymentCancel.builder()
                .payment(payment)
                .cancelReason("USER_CANCEL")
                .cancelAmount(amount)
                .build();
        ReflectionTestUtils.setField(cancel, "id", id);
        return cancel;
    }
}
