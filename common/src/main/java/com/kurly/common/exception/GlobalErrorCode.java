package com.kurly.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 커머스 서비스 전반에서 공통으로 사용하는 기본 에러 코드.
 * 도메인별 세부 에러 코드는 각 모듈에서 {@link ErrorCode}를 구현하여 확장한다.
 */
@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements ErrorCode {

    // 400 BAD_REQUEST
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON405", "지원하지 않는 HTTP 메서드입니다."),

    // 401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증이 필요합니다."),

    // 403 FORBIDDEN
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "접근 권한이 없습니다."),

    // 404 NOT_FOUND
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "요청한 리소스를 찾을 수 없습니다."),

    // 409 CONFLICT
    CONFLICT(HttpStatus.CONFLICT, "COMMON409", "요청이 현재 서버 상태와 충돌합니다."),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
