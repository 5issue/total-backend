import "../common/response.tsp";
import "../common/types.tsp";

namespace WmsService.Task;

@doc("""
  출고 지시 품목(OutboundItem) 1건을 피킹하는 작업 지시. `Task` 엔티티와 1:1 대응한다.
  보관존→피킹존 보충(REPLENISHMENT)은 이미 StockMovement가 자체 상태·확정 API를 갖고 있어
  Task로 다루지 않는다 — 그러면 상태를 두 곳에서 따로 관리해야 해서다. Task는 오직 피킹
  전용이며 outboundOrderId/outboundItemId는 항상 채워진다.
  """)
model TaskResponse {
  id: int64;
  taskNo: string;
  status: WmsService.Common.TaskStatus;
  warehouseId: int64;
  outboundOrderId: int64;
  outboundItemId: int64;

  @doc("작업자에게 배정되기 전(PENDING)에는 null.")
  workerId: int64 | null;

  startedAt: utcDateTime | null;
  completedAt: utcDateTime | null;
  createdAt: utcDateTime;
}

@doc("""
  대기 중인(PENDING) 피킹 작업을 작업자 1명이 잡아 시작한다. 별도의 "배정" API 없이 시작
  시점에 workerId를 받아 한 번에 배정+시작 처리한다. 이미 다른 작업자가 시작했거나
  PENDING이 아니면 409(WMS4096)로 실패한다.
  """)
model TaskStartRequest {
  taskId: int64;
  workerId: int64;
}

@doc("""
  진행 중인(IN_PROGRESS) 피킹 작업을 완료 처리한다. 이번 버전은 부분 피킹(shortage)을
  지원하지 않으며, 항상 주문 수량(orderedQuantity) 전체를 피킹한 것으로 기록한다.
  IN_PROGRESS 상태가 아니면 409(WMS4096), 작업을 시작한 워커와 다르면 409(WMS4097)로
  실패한다.
  """)
model TaskCompleteRequest {
  taskId: int64;
  workerId: int64;
}
