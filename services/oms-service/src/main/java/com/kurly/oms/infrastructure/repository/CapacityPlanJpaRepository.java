package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.fulfillment.CapacityPlan;
import com.kurly.oms.domain.fulfillment.CapacityPlanRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapacityPlanJpaRepository extends JpaRepository<CapacityPlan, Long>, CapacityPlanRepository {
}