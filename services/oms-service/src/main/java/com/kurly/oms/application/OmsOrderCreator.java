package com.kurly.oms.application;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OmsOrderCreator {
    private final OmsOrderRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(OmsOrder order) {
        repository.saveAndFlush(order);
    }
}
