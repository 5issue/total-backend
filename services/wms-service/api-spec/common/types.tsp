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
