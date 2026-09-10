package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentOutbox> findPendingForUpdateSkipLocked(@Param("limit") int limit);
}
