package com.kurly.common.exception;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException() {
        super(GlobalErrorCode.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(GlobalErrorCode.UNAUTHORIZED, message);
    }
}
