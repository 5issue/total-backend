package com.kurly.auth.exception;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * auth-service 전용 에러 코드. 공통 {@code GlobalErrorCode}로 표현되지 않는 것만 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    /** 연속 인증 실패로 일시 잠긴 계정. 공통 코드에 423이 없어 여기서 정의한다. */
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH423", "연속적인 비밀번호 오류로 요청이 거부 되었습니다."),

    /** 명세가 정한 잘못된 요청 코드. 지원하지 않는 소셜 제공자 등. */
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "AUTH400", "잘못된 요청입니다."),

    /** 인가 코드 위변조·만료, 소셜 제공자 통신 실패. */
    INVALID_AUTH_CODE(HttpStatus.UNAUTHORIZED, "AUTH401", "유효하지 않은 인가코드입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
