package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** 존재하지 않거나 <b>요청자 소유가 아닌</b> 결제. HTTP 404. */
public class PaymentNotFoundException extends BusinessException {

    public PaymentNotFoundException() {
        super(PaymentErrorCode.PAYMENT_NOT_FOUND);
    }
}
