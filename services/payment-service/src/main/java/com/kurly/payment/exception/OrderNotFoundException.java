package com.kurly.payment.exception;

import com.kurly.common.exception.BusinessException;

/** 존재하지 않거나 <b>요청자 소유가 아닌</b> 주문. HTTP 404. */
public class OrderNotFoundException extends BusinessException {

    public OrderNotFoundException() {
        super(PaymentErrorCode.ORDER_NOT_FOUND);
    }
}
