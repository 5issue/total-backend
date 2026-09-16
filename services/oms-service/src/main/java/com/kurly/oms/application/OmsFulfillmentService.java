package com.kurly.oms.application;


import com.kurly.oms.domain.fulfillment.CapacityPlanRepository;
import com.kurly.oms.domain.fulfillment.DeliverySlotRepository;
import com.kurly.oms.domain.fulfillment.TamRegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsFulfillmentService {

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

    @Transactional(readOnly = true)
    public Object getDeliveryPromises() {
        return null;
    }
}