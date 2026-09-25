package com.kurly.wms.application.event;

/**
 * OutboundOrder가 ALLOCATED 상태가 됐을 때(포함된 모든 OutboundItem이 피킹존 재고에 하드
 * 할당 완료) 발행한다. 주문 생성 시점에 바로 전부 할당되는 경우(`createFromOrderEvent`)와,
 * 처음엔 일부만 할당돼 PENDING_REPLENISHMENT로 대기하다가 나중에 보충이 끝나 마지막 품목까지
 * 채워지는 경우(`finalizeReplenishment`) 둘 다에서 발행한다. 피킹 작업 Task를 자동 생성하는
 * 소비자는 별도로 구현한다.
 */
public record OutboundAllocatedEvent(Long outboundOrderId) {
}
