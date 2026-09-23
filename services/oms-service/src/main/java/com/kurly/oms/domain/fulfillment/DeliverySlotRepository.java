package com.kurly.oms.domain.fulfillment;

import java.util.List;
import java.util.Optional;

public interface DeliverySlotRepository {

    Optional<DeliverySlot> findById(Long id);

    List<DeliverySlot> findByRegionIdAndIsActiveTrue(Long regionId);
}
