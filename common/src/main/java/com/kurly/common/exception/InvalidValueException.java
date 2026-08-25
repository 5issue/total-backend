package com.kurly.common.exception;

public class InvalidValueException extends BusinessException {

    public InvalidValueException() {
        super(GlobalErrorCode.INVALID_INPUT_VALUE);
    }

    public InvalidValueException(String message) {
        super(GlobalErrorCode.INVALID_INPUT_VALUE, message);
    }
}
