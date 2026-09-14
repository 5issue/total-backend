package com.kurly.product.domain.dto;

/**
 * 재고 선점/해제/확정 대상 한 건. REST 요청과 MQ 이벤트가 공통으로 참조하는 도메인 형태이며,
 * 전송 계층의 검증(bean validation)은 각 DTO(예: InventoryAdjustRequest.ReserveItemRequest)가 맡는다.
 */
public record ReserveItem(
        Long productId,
        Integer quantity
) {
}
