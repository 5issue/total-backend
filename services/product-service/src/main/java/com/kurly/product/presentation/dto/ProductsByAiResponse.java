package com.kurly.product.presentation.dto;

import java.util.List;

/**
 * @param items                요청한 ai_product_id 순서, 같은 값 안에서는 GROUP → UNIT 순
 * @param notFoundAiProductIds 일치하는 BE 상품이 없던 ai_product_id (요청 순서, 중복 제거)
 */
public record ProductsByAiResponse(
        List<ProductByAiItem> items,
        List<Long> notFoundAiProductIds
) {}
