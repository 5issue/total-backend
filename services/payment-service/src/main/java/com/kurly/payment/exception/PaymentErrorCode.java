package com.kurly.payment.exception;

import com.kurly.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * payment-service 전용 에러 코드. 공통 {@code GlobalErrorCode}로 표현되지 않는 것만 정의한다.
 *
 * <p>결제는 400으로 응답할 사유가 둘 이상이라 코드에 일련번호를 붙였다.
 * 코드 문자열은 로그·추적용이며, 응답의 {@code error} 필드에는 enum 이름이 실린다.
 */
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    /**
     * 요청 금액이 주문 금액과 다르다. 일반 입력 오류와 코드를 분리한 이유는
     * <b>금액 위변조는 별도로 추적·경보해야 할 사건</b>이기 때문이다. 같은 코드로 묶으면 지표에서 묻힌다.
     */
    AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAY4001", "요청된 결제 금액이 실제 주문 금액과 일치하지 않습니다."),

    /** 이미 취소됐거나 결제할 수 없는 상태. */
    INVALID_PAYMENT_STATUS(HttpStatus.BAD_REQUEST, "PAY4002", "이미 취소되었거나 유효하지 않은 결제 건입니다."),

    /** PG가 잔액 부족·한도 초과로 거절했다. 사용자가 수단을 바꿔 재시도해야 하므로 400과 구분한다. */
    PAYMENT_REQUIRED(HttpStatus.PAYMENT_REQUIRED, "PAY402", "결제 잔액이 부족하거나 한도를 초과했습니다."),

    /**
     * 존재하지 않거나 <b>요청자 소유가 아닌</b> 결제. 403으로 구분하면 ID를 훑어 남의 결제 존재
     * 여부를 알아낼 수 있다(시큐어코딩가이드 BE-06·BE-17).
     */
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY404", "존재하지 않는 결제 내역입니다."),

    /** 존재하지 않거나 요청자 소유가 아닌 주문. 같은 이유로 소유권 불일치를 구분하지 않는다. */
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "PAY4041", "존재하지 않는 주문입니다."),

    /** 같은 멱등키의 요청이 처리 중이거나 이미 완료됐다. */
    DUPLICATE_PAYMENT_REQUEST(HttpStatus.CONFLICT, "PAY409", "동일한 결제 요청이 처리 중이거나 이미 완료되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
