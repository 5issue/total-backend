namespace ProductService.Inventory;

model ReserveItemRequest {
  productId: int64;
  quantity: int32;
}

@doc("""
  `hold`/`release` 호출 시 재고를 예약/해제할 상품 목록을 지정한다. `items`는 타입상
  nullable이지만, 실제로 hold 경로에서는 null이면 NPE 위험이 있다(코드에 방어 로직 없이
  assert만 있음) — hold 호출 시에는 사실상 필수로 채워 보내야 한다.
  """)
model InventoryAdjustRequest {
  @doc("재고 HOLD/CONFIRM/RESTORE/RELEASE를 하나로 묶는 토큰 (UUID 문자열).")
  reservationToken: string;

  items: ReserveItemRequest[] | null;
}
