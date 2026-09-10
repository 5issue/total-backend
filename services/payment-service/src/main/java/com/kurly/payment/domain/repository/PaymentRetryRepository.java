package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentRetry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRetryRepository {

    <S extends PaymentRetry> S save(S paymentRetry);

    Optional<PaymentRetry> findById(Long id);

    /**
     * 배치가 실행할 대상. 예정 시각이 지난 PENDING 건을 오래된 순으로 가져온다.
     * SKIP LOCKED를 쓰는 이유는 {@link PaymentOutboxRepository}와 같다.
     */
    List<PaymentRetry> findDueForUpdateSkipLocked(LocalDateTime now, int limit);
}
