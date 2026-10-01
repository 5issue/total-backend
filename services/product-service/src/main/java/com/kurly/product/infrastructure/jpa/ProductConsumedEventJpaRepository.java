package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductConsumedEventJpaRepository extends JpaRepository<ProductConsumedEvent, Long> {

    boolean existsByEventId(String eventId);

    /**
     * 요청 키를 원자적으로 적재한다. 처음이면 1, 이미 있으면 0을 돌려준다.
     *
     * <p>확인 후 삽입(existsBy + save)은 두 트랜잭션이 동시에 "없음"을 보면 둘 다 INSERT 까지 가고, 두 번째가
     * UNIQUE 위반 예외로 끝난다. 그 예외는 리스너가 재시도 없이 DLQ 로 보내므로 중복 요청이 정상적으로 무시되지
     * 않는다. {@code ON CONFLICT DO NOTHING} 은 동시에 같은 키를 넣을 때 두 번째가 첫 번째 커밋을 기다렸다가
     * 충돌이면 예외 없이 0을 돌려주고, 첫 번째가 롤백되면 그대로 삽입해 처리를 이어 간다.
     */
    @Modifying
    @Query(value = "INSERT INTO product_consumed_event (event_id, event_type, consumed_at) "
            + "VALUES (:eventId, :eventType, now()) ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType);
}
