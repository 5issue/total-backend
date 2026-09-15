package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.WmsProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WmsProductJpaRepository extends JpaRepository<WmsProduct, Long> {
}
