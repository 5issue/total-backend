package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.fulfillment.FulfillmentCenter;
import com.kurly.oms.domain.fulfillment.FulfillmentCenterRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentCenterJpaRepository extends JpaRepository<FulfillmentCenter, Long>, FulfillmentCenterRepository {
}
