package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** 요청 금액이 주문 금액과 다르다. 금액 위변조 시도일 수 있어 별도 코드로 추적한다. HTTP 400. */
public class AmountMismatchException extends BusinessException {

    public AmountMismatchException() {
        super(PaymentErrorCode.AMOUNT_MISMATCH);
    }
}
