namespace WmsService.Common;

/**
 * 보관 유형. `com.kurly.wms.domain.enums.StorageType`와 1:1로 대응하며,
 * WmsProduct/Location 양쪽 엔티티와 적치 추천(putaway) 응답 등에서 공유한다.
 */
enum StorageType {
  REFRIGERATED,
  FROZEN,
  ROOM_TEMPERATURE,
}

/**
 * PDA/BO에서 사람이 수행하는 작업(적치 추천, 피킹 등)의 진행 상태.
 * 엔티티 자체의 상태 enum(InboundItemStatus 등)과는 별개로, 작업 화면 UI 상태를 표현한다.
 */
enum TaskStatus {
  PENDING,
  IN_PROGRESS,
  COMPLETED,
  CANCELED,
}

/**
 * 로케이션의 기능적 구역. `com.kurly.wms.infrastructure.entity.Location.Zone`과 1:1로 대응한다.
 * `LocationType`(PALLET_RACK/SHELF_BIN/BUFFER)과 사실상 1:1로 겹치는 값이라 중복처럼 보일 수
 * 있는데, 나중에 하나의 물리 구역(zone)에 여러 location_type이 섞이는 경우가 생기면 그때부터
 * 의미가 갈라진다고 보고 일단 별도 컬럼으로 유지한다.
 */
enum Zone {
  BUFFER,
  PICKING,
  STORAGE,
}
