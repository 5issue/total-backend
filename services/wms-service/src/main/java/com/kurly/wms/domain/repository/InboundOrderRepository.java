package com.kurly.wms.domain.repository;

import com.kurly.wms.infrastructure.entity.InboundOrder;

public interface InboundOrderRepository {
    InboundOrder save(InboundOrder inboundOrder);
}
