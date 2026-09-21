package com.kurly.oms.application;

import com.kurly.oms.domain.shipment.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsShipmentService {

    private final ShipmentRepository shipmentRepository;

    @Transactional
    public void rolloverShipment(Long shipmentId) {
    }
}
