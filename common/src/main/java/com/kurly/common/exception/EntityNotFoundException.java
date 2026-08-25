package com.kurly.common.exception;

public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException() {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND);
    }

    public EntityNotFoundException(String message) {
        super(GlobalErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
