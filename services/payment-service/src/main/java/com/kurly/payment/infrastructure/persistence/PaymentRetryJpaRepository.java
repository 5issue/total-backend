package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.repository.PaymentRetryRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRetryJpaRepository
        extends JpaRepository<PaymentRetry, Long>, PaymentRetryRepository {

    @Override
    <S extends PaymentRetry> S save(S entity);

    @Override
    Optional<PaymentRetry> findById(Long id);

    /** {@code FOR UPDATE SKIP LOCKED}는 JPQL로 표현할 수 없어 네이티브 쿼리를 쓴다. */
    @Override
    @Query(value = """
            SELECT * FROM payment_retries
             WHERE status = 'PENDING'
               AND next_retry_at <= :now
             ORDER BY next_retry_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentRetry> findDueForUpdateSkipLocked(@Param("now") LocalDateTime now,
                                                  @Param("limit") int limit);
}
