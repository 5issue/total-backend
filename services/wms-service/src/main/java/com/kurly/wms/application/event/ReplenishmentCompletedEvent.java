package com.kurly.wms.application.event;

import java.time.LocalDate;

/**
 * 보관존→피킹존 보충 지시(StockMovement, REPLENISHMENT)가 완료됐을 때 발행한다. 이 상품의
 * 피킹존 재고가 늘었으니, 할당을 미루고 있던(PENDING_REPLENISHMENT) 출고 상세를 최종
 * ALLOCATED로 완성할 수 있다는 신호다. 이 이동이 실제로 어디로/어떤 LOT으로 도착했는지까지
 * 실어서, 소비자가 다시 FEFO 조회를 하지 않고도 그대로 채워 넣을 수 있게 한다.
 */
public record ReplenishmentCompletedEvent(
        Long warehouseId,
        Long productId,
        Long locationId,
        String lotNo,
        LocalDate expiredDate,
        int quantity
) {
}
