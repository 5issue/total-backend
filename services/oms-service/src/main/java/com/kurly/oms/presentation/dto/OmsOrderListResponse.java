package com.kurly.oms.presentation.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record OmsOrderListResponse(
        long total,
        int page,
        int size,
        List<OmsOrderSummary> items
) {
    public static OmsOrderListResponse from(Page<OmsOrderSummary> page) {
        return new OmsOrderListResponse(
                page.getTotalElements(),
                page.getNumber() + 1,
                page.getSize(),
                page.getContent()
        );
    }
}