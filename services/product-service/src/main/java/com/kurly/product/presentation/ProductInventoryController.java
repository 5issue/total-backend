package com.kurly.product.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.Authenticated;
import com.kurly.common.security.PublicApi;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.product.application.ProductInventoryService;
import com.kurly.product.presentation.dto.InventoryAdjustRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/products/inventory")
public class ProductInventoryController {

    private final ProductInventoryService productInventoryService;

    @Authenticated
    @PostMapping("/hold")
    public ApiResponse<Void> hold(@Valid @RequestBody InventoryAdjustRequest request) {
        productInventoryService.hold(request.orderId(), request.items());
        return ApiResponse.success();
    }

    @PublicApi
    @PostMapping("/release")
    public ApiResponse<Void> release(@Valid @RequestBody InventoryAdjustRequest request) {
        productInventoryService.release(request.orderId(), request.items());
        return ApiResponse.success();
    }

    @PublicApi
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody InventoryAdjustRequest request) {
        productInventoryService.confirm(request.orderId(), request.items());
        return ApiResponse.success();
    }
}
