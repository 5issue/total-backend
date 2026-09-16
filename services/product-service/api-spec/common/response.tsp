import "@typespec/http";

using TypeSpec.Http;

namespace ProductService.Common;

/** `com.kurly.common.response.ResultStatus`와 1:1로 대응한다. */
enum ResultStatus {
  SUCCESS,
  ERROR,
}

/**
 * 전역 API 응답 포맷. `com.kurly.common.response.ApiResponse<T>`와 1:1로 대응한다.
 * 모든 엔드포인트는 이 포맷으로 감싼 응답을 반환한다.
 */
model ApiResponse<T> {
  status: ResultStatus;
  message: string;
  data: T | null;
  error: string | null;
  timestamp: utcDateTime;
}

/** 공통 에러 응답. `GlobalErrorCode` / 도메인별 `ProductErrorCode`의 `code` 값이 error 필드에 담긴다. */
@error
model ErrorResponse {
  status: ResultStatus.ERROR;
  message: string;
  data: null;
  error: string;
  timestamp: utcDateTime;
}

/** data가 항상 null인 성공 응답. `ApiResponse<Void>`(Java)에 대응한다. */
model NoDataResponse {
  status: ResultStatus.SUCCESS;
  message: string;
  data: null;
  error: null;
  timestamp: utcDateTime;
}
