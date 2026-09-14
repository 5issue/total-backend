package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductOutbox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductOutboxJpaRepository extends JpaRepository<ProductOutbox, Long> {

    @Query(value = """
            SELECT * FROM product_outbox
             WHERE status = 'PENDING'
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ProductOutbox> findPendingForUpdateSkipLocked(@Param("limit") int limit);

    /**
     * 커밋 직후 즉시 발행 경로가 특정 row 하나를 집어갈 때 쓴다. 스케줄러가 같은 row를 먼저
     * 잠갔다면 SKIP LOCKED로 빈 결과를 받아 중복 발행하지 않는다.
     */
    @Query(value = """
            SELECT * FROM product_outbox
             WHERE id = :id AND status = 'PENDING'
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<ProductOutbox> findPendingByIdForUpdateSkipLocked(@Param("id") Long id);
}
