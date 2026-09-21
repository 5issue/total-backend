package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.wms.application.StockMovementQueryService;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.presentation.dto.StockMovementConfirmRequest;
import com.kurly.wms.presentation.dto.StockMovementCreateRequest;
import com.kurly.wms.presentation.dto.StockMovementResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "StockMovement")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/stock-movements")
public class StockMovementController {

    private final StockMovementQueryService stockMovementQueryService;

    @GetMapping
    public ApiResponse<List<StockMovementResponse>> listStockMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(stockMovementQueryService.list(warehouseId, movementType, status, limit));
    }

    @PostMapping
    public ApiResponse<StockMovementResponse> createStockMovement(@Valid @RequestBody StockMovementCreateRequest request) {
        return ApiResponse.success(stockMovementQueryService.create(request));
    }

    @PostMapping("/confirm")
    public ApiResponse<StockMovementResponse> confirmStockMovement(@Valid @RequestBody StockMovementConfirmRequest request) {
        return ApiResponse.success(stockMovementQueryService.confirm(request));
    }
}
