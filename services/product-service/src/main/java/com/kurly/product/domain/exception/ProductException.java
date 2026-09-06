package com.kurly.product.domain.exception;

import com.kurly.common.exception.BusinessException;

public class ProductException extends BusinessException {

    public ProductException(ProductErrorCode errorCode) {
        super(errorCode);
    }

    public ProductException(ProductErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
