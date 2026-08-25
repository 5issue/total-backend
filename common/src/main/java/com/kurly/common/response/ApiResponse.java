package com.kurly.common.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.kurly.common.exception.ErrorCode;
import lombok.Getter;

import java.time.Instant;

/**
 * 전역 API 응답 포맷.
 * <pre>{@code
 * {
 *   "status": "SUCCESS",
 *   "message": "결제가 성공적으로 승인 및 완료되었습니다.",
 *   "data": {},
 *   "error": null,
 *   "timestamp": "2026-08-23T10:00:30Z"
 * }
 * }</pre>
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private final ResultStatus status;
    private final String message;
    private final T data;
    private final String error;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private final Instant timestamp;

    private ApiResponse(ResultStatus status, String message, T data, String error) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.error = error;
        this.timestamp = Instant.now();
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(ResultStatus.SUCCESS, "요청에 성공하였습니다.", null, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS, "요청에 성공하였습니다.", data, null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResultStatus.SUCCESS, message, data, null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return new ApiResponse<>(ResultStatus.ERROR, errorCode.getMessage(), null, errorCode.name());
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(ResultStatus.ERROR, message, null, errorCode.name());
    }
}
