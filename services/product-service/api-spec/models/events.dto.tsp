namespace ProductService.Events;

// ── product-service가 발행 ────────────────────────────────────────────

enum InventoryConfirmedStatus {
  CONFIRMED,
  INSUFFICIENT_STOCK,
}

model FailedItemInfo {
  productId: int64;
  requestedQuantity: int32;
}

@doc("""
  재고 확정 결과. `com.kurly.product.infrastructure.messaging.dto.ProductInventoryConfirmedEvent`와
  1:1로 대응한다. `failedItems`는 `INSUFFICIENT_STOCK`일 때만 채워진다(`@JsonInclude(NON_EMPTY)`
  — CONFIRMED면 빈 배열조차 아니라 필드 자체가 생략된다).
  """)
model ProductInventoryConfirmedEventPayload {
  eventId: string;
  routingKey: string;
  orderId: int64;
  status: InventoryConfirmedStatus;
  failedItems?: FailedItemInfo[];
  confirmedAt: utcDateTime;
}

enum InventoryRestoredStatus {
  RESTORED,
  ALREADY_RESTORED,
}

@doc("""
  재고 복구 결과. `com.kurly.product.infrastructure.messaging.dto.ProductInventoryRestoredEvent`와
  1:1로 대응한다. `ALREADY_RESTORED`는 이 값을 실제로 채우는 코드 경로가 없어(dead code)
  현재는 `RESTORED`만 발행된다.
  """)
model ProductInventoryRestoredEventPayload {
  eventId: string;
  routingKey: string;
  orderId: int64;
  status: InventoryRestoredStatus;
  restoredAt: utcDateTime;
}
