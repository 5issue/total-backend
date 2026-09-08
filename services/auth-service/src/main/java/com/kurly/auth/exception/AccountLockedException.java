package com.kurly.auth.exception;

import com.kurly.common.exception.BusinessException;

/** 연속 인증 실패로 일시 잠긴 계정의 로그인 시도. HTTP 423. */
public class AccountLockedException extends BusinessException {

    public AccountLockedException(String message) {
        super(AuthErrorCode.ACCOUNT_LOCKED, message);
    }
}
