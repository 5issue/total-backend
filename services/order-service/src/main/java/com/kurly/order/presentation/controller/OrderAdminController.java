package com.kurly.order.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.order.application.OrderClaimService;
import com.kurly.order.presentation.api.OrderAdminApi;
import com.kurly.order.presentation.dto.ReturnDetailResponse;
import com.kurly.order.presentation.dto.ReturnListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/admin/orders")
@RequireRole(Role.ADMIN)
@RequiredArgsConstructor
public class OrderAdminController implements OrderAdminApi {


    private final OrderClaimService orderClaimService;

    @Override
    @GetMapping("/returns")
    public ApiResponse<ReturnListResponse> listReturns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String storageType,
            Pageable pageable
    ) {
        List<String> storageTypes = storageType == null ? null :
                List.of(storageType.split(","));
        return ApiResponse.success("반품 신청 목록 조회에 성공했습니다.",
                orderClaimService.listReturns(status, storageTypes, pageable));
    }

    @Override
    @GetMapping("/returns/{returnId}")
    public ApiResponse<ReturnDetailResponse> getReturnDetail(@PathVariable Long returnId) {
        return ApiResponse.success("반품 상세 조회에 성공했습니다.",
                orderClaimService.getReturnDetail(returnId));
    }
}