package com.kurly.product.presentation.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 내부 서비스(주문 등)에서 여러 상품의 요약 정보를 한 번에 조회할 때 쓰는 요청.
 * productIds 는 실제 구매 단위인 UNIT 상품 id 를 기대한다.
 */
public record BatchProductSummaryRequest(
        @NotEmpty
        @Size(max = 100, message = "한 번에 조회할 수 있는 상품은 최대 100개입니다.")
        List<@NotNull Long> productIds
) {}
