package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.enums.RetryStatus;
import org.springframework.data.domain.Limit;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRetryRepository {

    <S extends PaymentRetry> S save(S paymentRetry);

    /** 배치가 실행할 대상. 예정 시각이 지난 PENDING 건을 오래된 순으로 가져온다. */
    List<PaymentRetry> findAllByStatusAndNextRetryAtBeforeOrderByNextRetryAtAsc(
            RetryStatus status, LocalDateTime now, Limit limit);
}
