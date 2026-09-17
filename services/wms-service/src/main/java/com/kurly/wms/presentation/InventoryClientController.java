package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.wms.application.StockMovementQueryService;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementStatus;
import com.kurly.wms.infrastructure.entity.StockMovement.MovementType;
import com.kurly.wms.presentation.dto.StockMovementResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** BO 어드민 / PDA 현장 작업자용 재고 API. 인증된 사용자만 호출할 수 있다(특정 역할 제한은 없음). */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/inventories")
public class InventoryClientController {

    private final StockMovementQueryService stockMovementQueryService;

    @GetMapping("/movement")
    public ApiResponse<List<StockMovementResponse>> listStockMovements(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.success(stockMovementQueryService.list(warehouseId, movementType, status, limit));
    }
}
