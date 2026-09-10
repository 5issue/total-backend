package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentOutboxJpaRepository
        extends JpaRepository<PaymentOutbox, Long>, PaymentOutboxRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    @Override
    <S extends PaymentOutbox> S save(S entity);

    /**
     * {@code FOR UPDATE SKIP LOCKED}는 JPQL로 표현할 수 없어 네이티브 쿼리를 쓴다.
     * MySQL 8 이상이 필요하다.
     */
    @Override
    @Query(value = """
            SELECT * FROM payment_outbox
             WHERE status = 'PENDING'
               AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentOutbox> findPendingForUpdateSkipLocked(@Param("now") LocalDateTime now,
                                                       @Param("limit") int limit);

    // LIMIT은 JPQL DELETE로 표현할 수 없어 네이티브 쿼리를 쓴다. 한 번에 지우는 양을 묶어 두지
    // 않으면 밀린 이벤트가 많을 때 한 트랜잭션이 테이블을 오래 잠근다.
    @Override
    @Modifying
    @Query(value = """
            DELETE FROM payment_outbox
             WHERE status = 'PUBLISHED'
               AND published_at < :before
             ORDER BY published_at ASC
             LIMIT :limit
            """, nativeQuery = true)
    int deletePublishedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
