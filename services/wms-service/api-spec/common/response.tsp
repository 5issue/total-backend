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

/**
 * 페이지네이션 목록 응답의 공통 형태. 단독으로 쓰지 않고 항상 `ApiResponse<PageResponse<T>>`처럼
 * `data` 필드 안에 담아 반환한다 — 이 프로젝트의 모든 응답이 `ApiResponse<T>`로 감싸야 하는
 * 규칙(docs/CLAUDE.md 5.6)은 페이지 응답도 예외가 아니다.
 */
model PageResponse<T> {
  content: T[];

  @doc("0부터 시작하는 현재 페이지 번호.")
  page: int32;

  size: int32;
  totalElements: int64;
}
