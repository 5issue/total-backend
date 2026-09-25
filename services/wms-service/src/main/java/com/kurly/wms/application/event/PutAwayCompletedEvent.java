package com.kurly.wms.application.event;

/**
 * 입고 적치(PUT_AWAY, BUFFER→STORAGE)가 완료됐을 때 발행한다. 이 상품의 보관존 재고가 늘었으니,
 * 보관존에서조차 보충 예약을 걸지 못했던(UNALLOCATED) 출고 상세를 다시 시도해볼 여지가 생겼다는
 * 신호다. 소비자(재시도 로직)는 별도로 구현한다.
 */
public record PutAwayCompletedEvent(Long warehouseId, Long productId) {
}
