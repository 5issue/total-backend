package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.fulfillment.TamRegion;
import com.kurly.oms.domain.fulfillment.TamRegionRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TamRegionJpaRepository extends JpaRepository<TamRegion, Long>, TamRegionRepository {
}
