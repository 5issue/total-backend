package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.domain.repository.InboundOrderRepository;
import com.kurly.wms.infrastructure.entity.InboundOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundOrderJpaRepository extends JpaRepository<InboundOrder, Long>, InboundOrderRepository {
}
