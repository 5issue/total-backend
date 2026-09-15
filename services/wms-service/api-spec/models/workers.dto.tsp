import "../common/response.tsp";

namespace WmsService.Workers;

// TODO: 이 도메인은 아직 Java 엔티티가 없다 (services/wms-service/src/main/java/com/kurly/wms/infrastructure/entity/
// 에 Worker 관련 엔티티 부재). API 계약을 먼저 잡아두는 단계이며, 실제 구현 전 엔티티/마이그레이션을 추가해야 한다.

/** 작업자의 실시간 근무 상태. */
enum WorkerStatus {
  AVAILABLE,
  WORKING,
  ON_BREAK,
  OFFLINE,
}

model WorkerRegisterRequest {
  name: string;
  warehouseId: int64;
}

model WorkerResponse {
  id: int64;
  name: string;
  warehouseId: int64;
  status: WorkerStatus;
}

model WorkerStatusUpdateRequest {
  status: WorkerStatus;
}
