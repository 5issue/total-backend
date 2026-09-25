package com.kurly.oms.domain.fulfillment;

import java.util.Optional;

public interface FulfillmentCenterRepository {

    Optional<FulfillmentCenter> findById(Long id);

}
