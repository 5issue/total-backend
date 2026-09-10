package com.kurly.user.exception;

import com.kurly.common.exception.BusinessException;

/** 존재하지 않거나 <b>요청자 소유가 아닌</b> 배송지. HTTP 404. */
public class AddressNotFoundException extends BusinessException {

    public AddressNotFoundException() {
        super(UserErrorCode.ADDRESS_NOT_FOUND);
    }
}
