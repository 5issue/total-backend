package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import com.kurly.wms.application.WarehouseQueryService;
import com.kurly.wms.domain.enums.Region;
import com.kurly.wms.presentation.dto.WarehouseResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OMS → WMS 내부 창고 조회 API. {@code /internal/**}은 인그레스에서 외부 노출을 막으므로
 * {@code @PublicApi}다(order/user-service의 internal 컨트롤러와 동일한 이유 — 호출자가
 * 서비스 간 통신이라 사용자 JWT 컨텍스트가 없다).
 */
@Tag(name = "Warehouse")
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/wms/warehouses")
public class WarehouseInternalController {

    private final WarehouseQueryService warehouseQueryService;

    @PublicApi
    @GetMapping
    public ApiResponse<List<WarehouseResponse>> listByRegion(
            @RequestParam(required = false) Region region,
            @RequestParam(required = false) Boolean isActive) {
        return ApiResponse.success(warehouseQueryService.listByRegion(region, isActive));
    }
}
