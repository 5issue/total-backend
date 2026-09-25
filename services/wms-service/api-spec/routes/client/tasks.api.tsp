import "@typespec/http";
import "../../common/response.tsp";
import "../../models/tasks.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Task;

@tag("Task")
@route("/api/v1/wms/tasks")
interface Task {
  @summary("피킹 작업 목록 조회")
  @doc("창고/상태 조건으로 피킹 Task 목록을 조회한다. 작업자가 자신이 잡을 수 있는 대기 목록(warehouseId + status=PENDING)을 확인하는 용도로 쓴다. createdAt 오름차순으로 정렬한다.")
  @get
  listTasks(@query warehouseId?: int64, @query status?: WmsService.Common.TaskStatus): WmsService.Common.ApiResponse<TaskResponse[]> | WmsService.Common.ErrorResponse;

  @summary("피킹 작업 시작(작업자 배정)")
  @doc("대기 중인(PENDING) 작업을 작업자 1명이 잡아 시작한다. 별도의 배정 API 없이 시작 시점에 workerId를 받아 한 번에 배정+시작 처리한다.")
  @post
  @route("/start")
  startTask(@body body: TaskStartRequest): WmsService.Common.ApiResponse<TaskResponse> | WmsService.Common.ErrorResponse;

  @summary("피킹 작업 완료")
  @doc("진행 중인(IN_PROGRESS) 작업을 완료 처리한다. 이번 버전은 항상 주문 수량 전체를 피킹한 것으로 기록한다(부분 피킹 미지원). 전표의 모든 품목이 피킹 완료되면 전표를 포장 대기 상태로 넘긴다.")
  @post
  @route("/complete")
  completeTask(@body body: TaskCompleteRequest): WmsService.Common.ApiResponse<TaskResponse> | WmsService.Common.ErrorResponse;
}
