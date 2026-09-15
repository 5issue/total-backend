import "@typespec/http";
import "../../common/response.tsp";
import "../../models/workers.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Workers;

@tag("Workers")
@route("/api/v1/wms/workers")
interface Workers {
  @summary("창고 작업자 등록")
  @post
  register(@body body: WorkerRegisterRequest): WmsService.Common.ApiResponse<WorkerResponse> | WmsService.Common.ErrorResponse;

  @summary("작업자 실시간 상태 변경")
  @patch
  @route("/{workerId}/status")
  updateStatus(@path workerId: int64, @body body: WorkerStatusUpdateRequest): WmsService.Common.ApiResponse<WorkerResponse> | WmsService.Common.ErrorResponse;
}
