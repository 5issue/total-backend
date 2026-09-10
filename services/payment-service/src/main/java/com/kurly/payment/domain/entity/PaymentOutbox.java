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

import java.time.Duration;
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

    /**
     * 백오프 기준 간격. 고정 간격으로 재시도하면 브로커 장애가 길어질 때 같은 부하를 계속 실어
     * 회복을 방해한다. 상한 5회 기준으로 10초·20초·40초·80초까지 미룬다.
     */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(10);

    /** DB 컬럼 길이. 넘치면 저장이 실패해 발행 실패가 트랜잭션 실패로 번진다. */
    private static final int LAST_ERROR_MAX_LENGTH = 255;

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

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    /**
     * 다음 발행 시각. {@code null}이면 지금 바로 대상이다(적재 직후가 그렇다).
     * 실패할 때마다 지수 백오프로 뒤로 민다.
     */
    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    private String lastError;

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
        this.lastError = null;
    }

    /**
     * 발행이 실패했다. 시도 횟수를 올리고 다음 시각을 지수 백오프로 미룬다.
     *
     * <p>상한에 도달하면 {@code FAILED}로 멈춘다. 페이로드가 깨졌거나 라우팅이 잘못된 이벤트는
     * 몇 번을 더 해도 나가지 않으면서 배치 묶음만 차지하고, 뒤에 쌓인 정상 이벤트를 밀어낸다.
     * 멈춘 뒤로는 사람이 봐야 하는 상태다({@code payment_retries}의 영구 실패와 같은 취급).
     */
    public void recordFailure(String error, int maxAttempts) {
        this.attemptCount++;
        this.lastError = truncate(error);
        if (attemptCount >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
            this.nextAttemptAt = null;
            return;
        }
        this.nextAttemptAt = LocalDateTime.now().plus(BASE_BACKOFF.multipliedBy(1L << (attemptCount - 1)));
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= LAST_ERROR_MAX_LENGTH ? error : error.substring(0, LAST_ERROR_MAX_LENGTH);
    }
}
