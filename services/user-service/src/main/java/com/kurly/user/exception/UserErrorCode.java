package com.kurly.user.exception;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * user-service 전용 에러 코드. 공통 {@code GlobalErrorCode}로 표현되지 않는 것만 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    /**
     * 배송지를 찾지 못함. <b>타인 소유 배송지도 이 코드로 응답한다.</b> 403으로 구분하면
     * "그 ID는 실재하지만 네 것이 아니다"를 알려주는 셈이어서, ID를 훑어 남의 배송지 존재 여부를
     * 알아낼 수 있다(시큐어코딩가이드 BE-06·BE-17).
     *
     * <p>공통 {@code RESOURCE_NOT_FOUND} 대신 두는 이유는 메시지를 배송지 문맥으로 특정하기 위함이다.
     */
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 배송지입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
