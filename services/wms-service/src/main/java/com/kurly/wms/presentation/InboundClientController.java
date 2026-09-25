package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import com.kurly.wms.application.InboundOrderService;
import com.kurly.wms.presentation.dto.InboundItemResponse;
import com.kurly.wms.presentation.dto.InspectItemRequest;
import com.kurly.wms.presentation.dto.PutAwayRecommendationRequest;
import com.kurly.wms.presentation.dto.PutAwayRecommendationResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BO 어드민 / PDA 현장 작업자용 입고 API. 인증된 사용자만 호출할 수 있다(특정 역할 제한은 없음). */
@Tag(name = "Inbound")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/inbounds")
public class InboundClientController {

    private final InboundOrderService inboundOrderService;

    @PublicApi
    @PostMapping("/inspect")
    public ApiResponse<InboundItemResponse> inspect(@Valid @RequestBody InspectItemRequest request) {
        return ApiResponse.success(inboundOrderService.inspect(request));
    }

    @PublicApi
    @PostMapping("/put-away/recommendation")
    public ApiResponse<PutAwayRecommendationResponse> recommendPutAway(@Valid @RequestBody PutAwayRecommendationRequest request) {
        return ApiResponse.success(inboundOrderService.recommendPutAway(request));
    }
}
