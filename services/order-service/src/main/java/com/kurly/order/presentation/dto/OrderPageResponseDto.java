package com.kurly.order.presentation.dto;

import com.kurly.order.domain.order.Order;
import org.springframework.data.domain.Page;
import java.util.List;

public record OrderPageResponseDto(long total, int page, int size, List<OrderResponseDto> orders) {
    public static OrderPageResponseDto from(Page<Order> result, int page, int size) {
        return new OrderPageResponseDto(result.getTotalElements(), page, size,
                result.getContent().stream().map(OrderResponseDto::from).toList());
    }
}
