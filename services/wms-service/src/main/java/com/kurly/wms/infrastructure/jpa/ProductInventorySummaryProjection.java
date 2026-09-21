package com.kurly.wms.infrastructure.jpa;

/** InventoryJpaRepository.summarize()의 GROUP BY 결과를 담는 JPQL 생성자 표현식 전용 프로젝션. */
public record ProductInventorySummaryProjection(
        Long productId,
        Long totalQuantity,
        Long totalReservedQuantity
) {
}
