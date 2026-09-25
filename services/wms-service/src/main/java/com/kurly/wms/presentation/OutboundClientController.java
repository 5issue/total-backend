package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.wms.application.OutboundOrderService;
import com.kurly.wms.presentation.dto.OutboundCompleteRequest;
import com.kurly.wms.presentation.dto.OutboundOrderResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** BO 어드민 / PDA 현장 작업자용 출고 API. 인증된 사용자만 호출할 수 있다(특정 역할 제한은 없음). */
@Tag(name = "Outbound")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/outbounds")
public class OutboundClientController {

    private final OutboundOrderService outboundOrderService;

    @PostMapping("/complete")
    public ApiResponse<OutboundOrderResponse> completeOutbound(@Valid @RequestBody OutboundCompleteRequest request) {
        return ApiResponse.success(outboundOrderService.completeShipment(request));
    }
}
