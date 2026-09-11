package com.kurly.product.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.product.application.InternalProductService;
import com.kurly.product.presentation.dto.BatchProductSummaryRequest;
import com.kurly.product.presentation.dto.BatchProductSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/products")
public class InternalProductController {

    private final InternalProductService internalProductService;

    @RequireRole(Role.ADMIN)
    @PostMapping("/batch-summary")
    public ApiResponse<BatchProductSummaryResponse> batchSummary(@Valid @RequestBody BatchProductSummaryRequest request) {
        return ApiResponse.success(internalProductService.getBatchSummary(request.productIds()));
    }
}
