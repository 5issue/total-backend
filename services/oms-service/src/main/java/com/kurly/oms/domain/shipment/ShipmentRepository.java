package com.kurly.oms.domain.shipment;

import java.util.List;

public interface ShipmentRepository {

    Shipment save(Shipment shipment);

    List<Shipment> findByOmsOrderIdWithItems(Long omsOrderId);

}
