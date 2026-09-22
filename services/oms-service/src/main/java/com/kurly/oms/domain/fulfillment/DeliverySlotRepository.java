package com.kurly.oms.domain.fulfillment;

import java.util.Optional;

public interface DeliverySlotRepository {

    Optional<DeliverySlot> findById(Long id);

}
