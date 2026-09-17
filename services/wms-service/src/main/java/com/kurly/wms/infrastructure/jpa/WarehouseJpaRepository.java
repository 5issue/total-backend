package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseJpaRepository extends JpaRepository<Warehouse, Long> {
}
