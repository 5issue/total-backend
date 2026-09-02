package com.kurly.order.domain.outbox;

public interface OrderOutboxRepository {

    OrderOutbox save(OrderOutbox orderOutbox);
}
