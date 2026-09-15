import "@typespec/http";

using TypeSpec.Http;

namespace WmsService.Common;

/** `com.kurly.common.response.ResultStatus`와 1:1로 대응한다. */
enum ResultStatus {
  SUCCESS,
  ERROR,
}

/**
 * 전역 API 응답 포맷. `com.kurly.common.response.ApiResponse<T>`와 1:1로 대응한다.
 * 모든 엔드포인트는 이 포맷으로 감싼 응답을 반환해야 한다 (docs/CLAUDE.md 5.6).
 */
model ApiResponse<T> {
  status: ResultStatus;
  message: string;
  data: T | null;
  error: string | null;
  timestamp: utcDateTime;
}

/** 공통 에러 응답. `GlobalErrorCode` / 도메인별 `ErrorCode`의 `code` 값이 error 필드에 담긴다. */
@error
model ErrorResponse {
  status: ResultStatus.ERROR;
  message: string;
  data: null;
  error: string;
  timestamp: utcDateTime;
}

@doc("배치 처리(sync 등)에서 개별 실패 항목을 알릴 때 사용.")
model BatchItemError {
  @doc("실패한 요청 항목을 식별하는 값 (예: productId).")
  identifier: string;
  reason: string;
}
