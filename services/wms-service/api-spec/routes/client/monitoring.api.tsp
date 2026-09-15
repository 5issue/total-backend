import "@typespec/http";
import "../../common/response.tsp";

using TypeSpec.Http;

namespace WmsService.Monitoring;

// [placeholder] 히트맵/UPH 집계 방식이 아직 정의되지 않았다 (docs/erd-spec.md에도 없음).
// 화면/계약 형태만 미리 잡아둔 것이며, 지표 정의가 확정되면 모델과 엔드포인트를 다시 설계한다.
// 도메인이 커지면 이 파일의 모델을 models/monitoring.dto.tsp로 분리한다.

model LocationHeatmapCell {
  locationId: int64;
  zone: string | null;

  @doc("가용 용량 대비 사용률 (0.0 ~ 1.0).")
  utilizationRate: float32;
}

model LocationHeatmapResponse {
  warehouseId: int64;
  cells: LocationHeatmapCell[];
}

model WorkerUphResponse {
  workerId: int64;
  date: plainDate;

  @doc("시간당 처리 수량 (Units Per Hour).")
  unitsPerHour: float32;
}

@tag("Monitoring")
@route("/api/v1/wms/monitoring")
interface Monitoring {
  @summary("[placeholder] 창고 히트맵 조회")
  @doc("창고 내 로케이션별 재고 적재율을 시각화용 데이터로 조회한다.")
  @get
  @route("/heatmap")
  getHeatmap(@query warehouseId: int64): WmsService.Common.ApiResponse<LocationHeatmapResponse> | WmsService.Common.ErrorResponse;

  @summary("[placeholder] 작업자 생산성(UPH) 조회")
  @doc("작업자별 시간당 처리 수량(Units Per Hour) 통계를 조회한다.")
  @get
  @route("/worker-uph")
  getWorkerUph(@query workerId: int64, @query date: plainDate): WmsService.Common.ApiResponse<WorkerUphResponse> | WmsService.Common.ErrorResponse;
}
