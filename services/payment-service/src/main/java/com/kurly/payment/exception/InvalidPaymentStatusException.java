package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** 결제가 요청한 작업을 수행할 수 없는 상태다. HTTP 400. */
public class InvalidPaymentStatusException extends BusinessException {

    public InvalidPaymentStatusException() {
        super(PaymentErrorCode.INVALID_PAYMENT_STATUS);
    }
}
