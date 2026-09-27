package com.kurly.product.infrastructure.entity;

import com.kurly.product.domain.enums.ConsumedEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 인박스(멱등성 마커) 테이블. WMS 등 외부 서비스가 발행한 이벤트를 처리하기 전, 같은 트랜잭션
 * 안에서 이 테이블에 event_id를 먼저 적재해 재전달로 인한 중복 처리를 막는다.
 *
 * <p>{@code event_id}의 UNIQUE 제약은 안전망이다 — 같은 메시지는 큐에서 동시가 아니라
 * 순차적으로만 재전달되므로 존재 여부 확인 후 삽입으로 충분하고, 위반이 발생하면(사실상
 * 발생하지 않아야 함) 예외를 그대로 올려 리스너의 재시도/DLQ 경로로 보낸다.
 */
@Entity
@Table(name = "product_consumed_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductConsumedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private ConsumedEventType eventType;

    @CreationTimestamp
    @Column(name = "consumed_at", nullable = false, updatable = false)
    private LocalDateTime consumedAt;

    @Builder
    private ProductConsumedEvent(String eventId, ConsumedEventType eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }
}
