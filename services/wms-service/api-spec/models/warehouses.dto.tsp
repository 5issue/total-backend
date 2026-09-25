namespace WmsService.Warehouses;

/**
 * 물류 배송 권역. `com.kurly.wms.domain.enums.Region`과 1:1로 대응한다. 광역자치단체(시/도)
 * 17개를 그대로 쓰지 않고 물류 배정에 의미 있는 단위로 묶었다 — 경기도는 이 회사의 물류센터가
 * 여러 곳 몰려 있어(김포/평택/안산) 동/서로 더 쪼갰고, 나머지 지역은 배송 권역 단위로 크게
 * 묶었다. `Warehouse.regions`가 어느 권역들을 담당하는지, OMS가 배송지 주소로 판단한 권역을
 * 어떤 창고가 커버하는지 조회할 때 쓴다. 한 창고가 여러 권역을 동시에 담당할 수도 있고(예:
 * 김포물류센터가 경기서부+수도권을 같이 커버), 반대로 여러 창고가 같은 권역을 공유할 수도
 * 있다(예: 경기서부 물류센터 여러 곳) — 그중 하나를 고르는 기준(거리/부하 분산 등)은 이 API의
 * 책임 범위 밖이다.
 */
enum Region {
  SEOUL_METRO,
  GYEONGGI_EAST,
  GYEONGGI_WEST,
  CHUNGCHEONG,
  GANGWON,
  YEONGNAM,
  HONAM,
  JEJU,
}

/** `Warehouse` 엔티티와 대응하는 응답. */
model WarehouseResponse {
  id: int64;
  code: string;
  name: string;
  address: string;

  @doc("이 창고가 담당하는 물류 배송 권역들. 한 창고가 여러 권역을 커버할 수 있어 배열이다. 아직 배정되지 않았으면 빈 배열.")
  regions: Region[];

  isActive: boolean;
}
