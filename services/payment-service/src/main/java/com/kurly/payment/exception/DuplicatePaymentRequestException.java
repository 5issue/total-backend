package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** 같은 멱등키의 요청이 처리 중이거나 이미 완료됐다. HTTP 409. */
public class DuplicatePaymentRequestException extends BusinessException {

    public DuplicatePaymentRequestException() {
        super(PaymentErrorCode.DUPLICATE_PAYMENT_REQUEST);
    }
}
