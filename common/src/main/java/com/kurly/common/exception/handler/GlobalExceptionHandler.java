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
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
