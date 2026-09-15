package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.InboundItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InboundItemJpaRepository extends JpaRepository<InboundItem, Long> {
}
