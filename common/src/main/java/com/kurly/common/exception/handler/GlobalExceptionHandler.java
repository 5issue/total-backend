package com.kurly.common.exception.handler;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.ErrorCode;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@AutoConfiguration
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), e.getMessage(), e);
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        log.warn("MethodArgumentNotValidException: {}", message);
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE, message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage()))
                .collect(Collectors.joining(", "));
        log.warn("ConstraintViolationException: {}", message);
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE, message));
    }

    /**
     * 본문을 읽지 못한 경우. JSON 문법 오류, 타입 불일치, enum에 없는 값 등이 여기로 온다.
     * 처리하지 않으면 클라이언트 입력 오류가 500으로 나가 서버 결함처럼 보인다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        // 파싱 실패 상세에는 내부 타입명이 담기므로 응답에 싣지 않는다(시큐어코딩가이드 BE-17).
        log.warn("요청 본문을 해석하지 못함: {}", e.getMessage());
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * 경로 변수·쿼리 파라미터를 선언 타입으로 바꾸지 못한 경우. {@code /addresses/abc/default}처럼
     * 숫자 자리에 문자가 온 요청이 여기로 온다. 처리하지 않으면 클라이언트 잘못인데도 500이 나간다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        // 기대 타입명은 내부 구현이므로 응답에 싣지 않는다(시큐어코딩가이드 BE-17).
        log.warn("요청 파라미터 타입이 맞지 않음: name={}", e.getName());
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    /**
     * 매핑된 핸들러가 없는 경로. 처리하지 않으면 catch-all로 떨어져 <b>존재하지 않는 URL이 500</b>이 된다.
     *
     * <p>500은 서버 결함을 뜻하므로, 오타나 스캐너의 무작위 요청이 장애 지표를 오염시키고
     * 실제 장애를 가린다. 어떤 경로가 실재하는지는 드러내지 않은 채 404로 응답한다.
     */
    /**
     * 필수 요청값이 아예 오지 않은 경우. 헤더({@code Idempotency-Key} 등)·쿼리 파라미터·쿠키가 해당된다.
     *
     * <p>처리하지 않으면 catch-all로 떨어져 <b>클라이언트가 헤더를 빠뜨렸을 뿐인데 500</b>이 나간다.
     * 경로 변수 누락({@code MissingPathVariableException})은 매핑이 잘못됐다는 뜻이라 여기서 다루지
     * 않는다. 그것은 실제로 서버 결함이다.
     */
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingRequestValueException(MissingRequestValueException e) {
        log.warn("필수 요청값 누락: {}", e.getMessage());
        return ResponseEntity.status(GlobalErrorCode.INVALID_INPUT_VALUE.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.INVALID_INPUT_VALUE));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleNoHandlerFound(Exception e) {
        // 서블릿 환경에서는 NoResourceFoundException이, 핸들러 매핑만 구성된 환경에서는
        // NoHandlerFoundException이 올라온다. 둘 다 "그런 경로 없음"이라 같이 다룬다.
        log.debug("매핑되지 않은 경로 요청: {}", e.getMessage());
        return ResponseEntity.status(GlobalErrorCode.RESOURCE_NOT_FOUND.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("HttpRequestMethodNotSupportedException: {}", e.getMessage());
        return ResponseEntity.status(GlobalErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(GlobalErrorCode.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception occurred", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(GlobalErrorCode.INTERNAL_SERVER_ERROR));
    }
}
