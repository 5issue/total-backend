package com.kurly.wms.presentation.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** api-spec의 `WmsService.Common.PageResponse<T>`와 1:1로 대응하는 공용 페이지 응답. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
