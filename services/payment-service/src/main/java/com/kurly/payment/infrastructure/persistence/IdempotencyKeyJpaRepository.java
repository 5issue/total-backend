package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, Long>, IdempotencyKeyRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends IdempotencyKey> S save(S entity);

    @Override
    Optional<IdempotencyKey> findById(Long id);

    @Override
    void deleteById(Long id);

    // LIMIT은 JPQL DELETE로 표현할 수 없어 네이티브 쿼리를 쓴다. 한 번에 지우는 양을 묶어 두지
    // 않으면 밀린 키가 많을 때 한 트랜잭션이 테이블을 오래 잠근다.
    @Override
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
             WHERE status = 'IN_PROGRESS'
               AND created_at < :staleBefore
             ORDER BY created_at ASC
             LIMIT :limit
            """, nativeQuery = true)
    int deleteStaleInProgress(@Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);

    @Override
    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
             WHERE status = 'COMPLETED'
               AND created_at < :before
             ORDER BY created_at ASC
             LIMIT :limit
            """, nativeQuery = true)
    int deleteCompletedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);
}
