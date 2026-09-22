import "@typespec/http";
import "../../common/response.tsp";
import "../../models/warehouses.dto.tsp";

using TypeSpec.Http;

namespace WmsService.Warehouses;

@tag("Warehouse")
@route("/internal/v1/wms/warehouses")
interface WarehousesInternal {
  @summary("권역별 물류 창고 조회")
  @doc("""
    OMS가 배송지 주소로부터 판단한 물류 배송 권역(region)에 맞는 배정 대상 창고를 찾을 때
    쓴다. region을 넘기면 그 권역을 담당 권역 목록(`regions`)에 포함하는 창고만 돌려준다.
    생략하면 조건에 맞는(기본은 운영 중인) 창고 전체를 반환한다. 창고 수가 많지 않아
    페이지네이션 없이 배열 전체를 반환한다.

    한 창고가 여러 권역을 동시에 담당할 수도 있고(예: 김포물류센터가 경기서부+수도권을
    같이 커버), 같은 권역을 여러 창고가 담당할 수도 있다(예: 경기서부 물류센터 여러 곳) —
    그중 어느 창고를 최종 배정할지(거리/부하 분산 등)는 이 API가 정하지 않고 후보를
    그대로 돌려준다.
    """)
  @get
  listByRegion(
    @query region?: Region,

    @doc("기본값 true — 운영 중이 아닌(LOCKED 등) 창고까지 보려면 명시적으로 false를 넘긴다.")
    @query isActive?: boolean
  ): WmsService.Common.ApiResponse<WarehouseResponse[]> | WmsService.Common.ErrorResponse;
}
