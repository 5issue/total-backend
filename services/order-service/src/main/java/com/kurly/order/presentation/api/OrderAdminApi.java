package com.kurly.order.presentation.api;

import com.kurly.common.response.ApiResponse;
import com.kurly.order.presentation.dto.ReturnDetailResponse;
import com.kurly.order.presentation.dto.ReturnListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "주문 관리자", description = "주문 Admin/BO API")
public interface OrderAdminApi {

    @Operation(summary = "반품 신청 목록 조회", description = "접수된 반품 신청 건을 조회합니다.")
    ApiResponse<ReturnListResponse> listReturns(
            @Parameter(description = "반품 상태") String status,
            @Parameter(description = "보관 온도대") String storageType,
            @Parameter(hidden = true) Pageable pageable
    );

    @Operation(summary = "반품 신청 상세 조회", description = "반품 건의 증빙, 사유, 상품 정보를 조회합니다.")
    ApiResponse<ReturnDetailResponse> getReturnDetail(
            @Parameter(description = "반품 ID") Long returnId
    );
}