package com.kurly.order.presentation.dto;

import java.time.LocalDateTime;

/**
 * 배송 완료 처리 결과.
 *
 * @param fridgeSynced AI 냉장고 적재까지 성공했는지. <b>{@code false}여도 배송 완료는 확정된다.</b>
 *                     부가 연동 실패가 주문 상태 전이를 막지 않는다. 운영자가 누락을 알아볼 수
 *                     있도록 응답에 드러낸다.
 */
public record DeliveryCompleteResponseDto(
        Long orderId,
        String deliveryStatus,
        LocalDateTime deliveredAt,
        boolean fridgeSynced
) {
}
