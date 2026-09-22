package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.shipment.Shipment;
import com.kurly.oms.domain.shipment.ShipmentRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShipmentJpaRepository extends JpaRepository<Shipment, Long>, ShipmentRepository {

    @Query("""
            SELECT DISTINCT s FROM Shipment s
            LEFT JOIN FETCH s.items
            WHERE s.omsOrderId = :omsOrderId
            """)
    List<Shipment> findByOmsOrderIdWithItems(@Param("omsOrderId") Long omsOrderId);
    
}
