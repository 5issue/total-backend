package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** PG가 잔액 부족·한도 초과로 거절했다. HTTP 402. */
public class PaymentDeclinedException extends BusinessException {

    public PaymentDeclinedException() {
        super(PaymentErrorCode.PAYMENT_REQUIRED);
    }
}
