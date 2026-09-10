package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentCancelJpaRepository extends JpaRepository<PaymentCancel, Long>, PaymentCancelRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends PaymentCancel> S save(S entity);

    @Override
    Optional<PaymentCancel> findById(Long id);

    /** {@code FOR UPDATE SKIP LOCKED}는 JPQL로 표현할 수 없어 네이티브 쿼리를 쓴다. */
    @Override
    @Query(value = """
            SELECT * FROM payment_cancels
             WHERE status = 'REQUESTED'
               AND created_at < :staleBefore
             ORDER BY created_at ASC
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<PaymentCancel> findStaleRequestedForUpdateSkipLocked(
            @Param("staleBefore") LocalDateTime staleBefore, @Param("limit") int limit);
}
