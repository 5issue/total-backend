package com.kurly.oms.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import com.kurly.oms.application.OmsFulfillmentService;
import com.kurly.oms.application.OmsOrderService;
import com.kurly.oms.presentation.api.OmsInternalApi;
import com.kurly.oms.presentation.dto.CancelEligibilityResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/oms")
@PublicApi
@RequiredArgsConstructor
public class OmsInternalController implements OmsInternalApi {

    private final OmsOrderService orderService;
    private final OmsFulfillmentService fulfillmentService;

    @Override
    @PostMapping("/delivery-promises")
    public ApiResponse<Object> getDeliveryPromises() {
        return ApiResponse.success(fulfillmentService.getDeliveryPromises());
    }

    @Override
    @GetMapping("/orders/{orderId}/cancel-eligibility")
    public ApiResponse<CancelEligibilityResponseDto> checkCancelEligibility(@PathVariable Long orderId) {
        return ApiResponse.success(orderService.checkCancelEligibility(orderId));
    }

}