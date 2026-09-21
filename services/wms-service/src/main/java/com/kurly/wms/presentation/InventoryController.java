package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.wms.application.InventoryService;
import com.kurly.wms.infrastructure.entity.Location.Zone;
import com.kurly.wms.presentation.dto.InventoryDetailResponse;
import com.kurly.wms.presentation.dto.PageResponse;
import com.kurly.wms.presentation.dto.ProductInventorySummaryResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Inventory")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/inventories")
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/summary")
    public ApiResponse<PageResponse<ProductInventorySummaryResponse>> getInventorySummary(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(inventoryService.summarize(warehouseId, productId, page, size));
    }

    @GetMapping
    public ApiResponse<PageResponse<InventoryDetailResponse>> listInventoryDetails(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) Zone zone,
            @RequestParam(required = false) Long locationId,
            @RequestParam(required = false) String lotNo,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        return ApiResponse.success(inventoryService.search(warehouseId, productId, zone, locationId, lotNo, page, size));
    }
}
