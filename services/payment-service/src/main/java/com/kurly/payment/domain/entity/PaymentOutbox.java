package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.OutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 이벤트 아웃박스.
 *
 * <p>업무 트랜잭션과 <b>같은 트랜잭션에서</b> 이벤트를 저장하고, 발행은 별도 워커가 맡는다.
 * 브로커 발행을 업무 트랜잭션에 섞으면 DB는 커밋됐는데 발행이 실패하거나 그 반대가 되어
 * 결제 상태와 후속 처리가 어긋난다.
 *
 * <p>{@code eventId}는 소비자의 중복 처리 방어 기준이다. 발행은 최소 1회를 보장하므로 소비자는
 * 같은 이벤트를 두 번 받을 수 있다(주문-결제 시퀀스 2절).
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    /**
     * 이벤트 본문. <b>JWT 원문은 싣지 않는다.</b> 큐는 영속화되고 재시도·DLQ로 오래 남아
     * 토큰이 그대로 남으면 자격증명 유출이 된다. userId 등 식별정보만 담는다(설계서 3.4).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Builder
    private PaymentOutbox(String eventType, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    /** 반복 실패로 발행을 중단한다. 사람이 봐야 하는 상태다. */
    public void markFailed() {
        this.status = OutboxStatus.FAILED;
    }
}
