package com.kurly.product.application.support;

import com.kurly.product.presentation.dto.ProductFilterResponse.FilterItemDto;
import com.kurly.product.domain.enums.ProductSortType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FilterLayoutProvider {

    public List<FilterItemDto> getSortFilters() {
        return List.of(
                new FilterItemDto("추천순", ProductSortType.RECOMMENDED.name(), 0),
                new FilterItemDto("신상품순", ProductSortType.LATEST.name(), 0),
                new FilterItemDto("판매량순", ProductSortType.POPULAR.name(), 0),
                new FilterItemDto("혜택순", ProductSortType.BENEFIT.name(), 0),
                new FilterItemDto("높은가격순", ProductSortType.PRICE_DESC.name(), 0),
                new FilterItemDto("낮은가격순", ProductSortType.PRICE_ASC.name(), 0)
        );
    }
}
