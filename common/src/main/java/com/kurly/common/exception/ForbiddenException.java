package com.kurly.common.exception;

/**
 * 인증은 되었으나 권한이 없는 경우. 인증인가_설계서 2.4 — 401과 403을 구분한다.
 *
 * <p>역할 부족, 타인 리소스 접근 시 사용한다. 인증 자체가 되지 않은 경우는
 * {@link UnauthorizedException}(401)을 쓴다.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException() {
        super(GlobalErrorCode.FORBIDDEN);
    }

    public ForbiddenException(String message) {
        super(GlobalErrorCode.FORBIDDEN, message);
    }
}
