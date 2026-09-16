package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.shipment.Shipment;
import com.kurly.oms.domain.shipment.ShipmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentJpaRepository extends JpaRepository<Shipment, Long>, ShipmentRepository {
}
