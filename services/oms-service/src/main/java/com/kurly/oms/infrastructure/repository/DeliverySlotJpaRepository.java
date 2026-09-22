package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.fulfillment.DeliverySlot;
import com.kurly.oms.domain.fulfillment.DeliverySlotRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverySlotJpaRepository extends JpaRepository<DeliverySlot, Long>, DeliverySlotRepository {
}
