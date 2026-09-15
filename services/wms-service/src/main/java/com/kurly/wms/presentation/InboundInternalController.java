package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.PublicApi;
import com.kurly.wms.application.InboundOrderService;
import com.kurly.wms.presentation.dto.InboundAsnCreateRequest;
import com.kurly.wms.presentation.dto.InboundOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SCM → WMS 내부 입고 API. {@code /internal/**}은 인그레스에서 외부 노출을 막으므로
 * {@code @PublicApi}다(order/user-service의 internal 컨트롤러와 동일한 이유 — 호출자가
 * 서비스 간 통신이라 사용자 JWT 컨텍스트가 없다).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/wms/inbounds")
public class InboundInternalController {

    private final InboundOrderService inboundOrderService;

    @PublicApi
    @PostMapping("/asn")
    public ApiResponse<InboundOrderResponse> createAsn(@Valid @RequestBody InboundAsnCreateRequest request) {
        return ApiResponse.success(inboundOrderService.createAsn(request));
    }
}
