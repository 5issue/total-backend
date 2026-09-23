package com.kurly.oms.application;


import com.kurly.common.exception.BusinessException;
import com.kurly.oms.domain.common.OmsErrorCode;
import com.kurly.oms.domain.fulfillment.*;
import com.kurly.oms.presentation.dto.DeliveryPromiseRequest;
import com.kurly.oms.presentation.dto.DeliveryPromiseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class OmsFulfillmentService {

    private static final ZoneId DELIVERY_ZONE = ZoneId.of("Asia/Seoul");

    private final CapacityPlanRepository capacityPlanRepository;
    private final TamRegionRepository tamRegionRepository;
    private final DeliverySlotRepository deliverySlotRepository;

    @Transactional(readOnly = true)
    public Object listCapacities() {
        return null;
    }

    @Transactional
    public void adjustCapacity(Long capacityPlanId) {
    }

    @Transactional(readOnly = true)
    public Object listRegions() {
        return null;
    }

    @Transactional
    public void editRegionStatus(Long regionId) {
    }

    @Transactional(readOnly = true)
    public Object listDeliverySlots() {
        return null;
    }

    @Transactional
    public void editDeliverySlotCutoff(Long slotId) {
    }

    public DeliveryPromiseResponse getDeliveryPromises(DeliveryPromiseRequest request) {
        String regionCode = parseRegionCode(request.address());

        TamRegion region = tamRegionRepository.findByRegionCodeAndStatus(regionCode, RegionStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(OmsErrorCode.ORD_NOT_FOUND_ADDRESS));

        DeliverySlot slot = deliverySlotRepository.findByRegionIdAndIsActiveTrue(region.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(OmsErrorCode.ORD_NOT_FOUND_ADDRESS));

        LocalDate today = LocalDate.now(DELIVERY_ZONE);
        LocalTime nowTime = LocalTime.now(DELIVERY_ZONE);

        LocalDate deliveryDate = today.plusDays(slot.getLeadDays());
        if (nowTime.isAfter(slot.getCutoffTime())) {
            deliveryDate = deliveryDate.plusDays(1);
        }

        Instant cutoffAt = slot.getCutoffTime().atDate(today).atZone(DELIVERY_ZONE).toInstant();
        Instant expectedDeliveryAt = slot.getDeliveryEndTime().atDate(deliveryDate).atZone(DELIVERY_ZONE).toInstant();

        return new DeliveryPromiseResponse(
                true,
                region.getId(),
                region.getDeliveryType(),
                cutoffAt,
                expectedDeliveryAt
        );
    }

    private String parseRegionCode(String address) {
        if (address == null || address.isBlank()) {
            return "PARCEL_NATIONWIDE";
        }

        if (address.contains("서울") || address.contains("경기") || address.contains("인천")) {
            return "DAWN_SEOUL_METRO";
        } else if (address.contains("부산")) {
            return "DAWN_BUSAN";
        } else if (address.contains("대구")) {
            return "DAWN_DAEGU";
        } else if (address.contains("대전")) {
            return "DAWN_DAEJEON";
        } else if (address.contains("광주")) {
            return "DAWN_GWANGJU";
        } else if (address.contains("울산")) {
            return "DAWN_ULSAN";
        }

        return "PARCEL_NATIONWIDE"; // 그 외 경남, 경북, 전남, 전북, 충남, 충북, 강원, 제주 등
    }
}
