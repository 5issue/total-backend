package com.kurly.oms.presentation.controller;

import com.kurly.common.response.ApiResponse;
import com.kurly.common.security.RequireRole;
import com.kurly.common.security.Role;
import com.kurly.oms.application.OmsFulfillmentService;
import com.kurly.oms.application.OmsShipmentService;
import com.kurly.oms.presentation.api.OmsAdminFulfillmentApi;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/oms")
@RequireRole(Role.ADMIN)
@RequiredArgsConstructor
public class OmsAdminFulfillmentController implements OmsAdminFulfillmentApi {

    private final OmsFulfillmentService fulfillmentService;
    private final OmsShipmentService shipmentService;

    @Override
    @PatchMapping("/capacities/{capacityPlanId}")
    public ApiResponse<Void> adjustCapacity(@PathVariable Long capacityPlanId) {
        fulfillmentService.adjustCapacity(capacityPlanId);
        return ApiResponse.success();
    }

    @Override
    @GetMapping("/capacities")
    public ApiResponse<Object> listCapacities() {
        return ApiResponse.success(fulfillmentService.listCapacities());
    }

    @Override
    @PostMapping("/shipments/{shipmentId}/rollover")
    public ApiResponse<Void> rolloverShipment(@PathVariable Long shipmentId) {
        shipmentService.rolloverShipment(shipmentId);
        return ApiResponse.success();
    }

    @Override
    @PatchMapping("/delivery-slots/{slotId}")
    public ApiResponse<Void> editDeliverySlotCutoff(@PathVariable Long slotId) {
        fulfillmentService.editDeliverySlotCutoff(slotId);
        return ApiResponse.success();
    }

    @Override
    @GetMapping("/regions")
    public ApiResponse<Object> listRegions() {
        return ApiResponse.success(fulfillmentService.listRegions());
    }

    @Override
    @PatchMapping("/regions/{regionId}")
    public ApiResponse<Void> editRegionStatus(@PathVariable Long regionId) {
        fulfillmentService.editRegionStatus(regionId);
        return ApiResponse.success();
    }

    @Override
    @GetMapping("/delivery-slots")
    public ApiResponse<Object> listDeliverySlots() {
        return ApiResponse.success(fulfillmentService.listDeliverySlots());
    }
}