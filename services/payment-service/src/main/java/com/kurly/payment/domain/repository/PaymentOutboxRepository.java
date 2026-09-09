package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.enums.OutboxStatus;
import org.springframework.data.domain.Limit;

import java.util.List;

public interface PaymentOutboxRepository {

    <S extends PaymentOutbox> S save(S paymentOutbox);

    /**
     * 발행 워커가 집어갈 대상. 오래된 순으로 가져와 발행 순서를 최대한 보존한다.
     * 한 번에 가져오는 양을 제한해 워커 한 주기가 길어지지 않게 한다.
     */
    List<PaymentOutbox> findAllByStatusOrderByCreatedAtAsc(OutboxStatus status, Limit limit);
}
