package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.RetryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 재시도 큐.
 *
 * <p>PG 취소 실패처럼 즉시 성공하지 못한 작업을 배치가 다시 집어간다(주문-결제 시퀀스 1절 단계 3).
 * 결제 유효시간 초과로 보상 취소를 걸었는데 그 취소마저 실패하면, 고객 돈이 묶인 채로 남으므로
 * 반드시 재시도가 보장되어야 한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_retries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentRetry {

    /** 재시도 간격의 기준. 시도할수록 2배씩 늘린다. */
    private static final Duration BASE_BACKOFF = Duration.ofMinutes(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /** 작업 종류. 예: {@code PG_CANCEL} */
    @Column(name = "task_type", nullable = false, length = 50)
    private String taskType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload")
    private String payload;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private RetryStatus status;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private PaymentRetry(Payment payment, String taskType, String payload) {
        this.payment = payment;
        this.taskType = taskType;
        this.payload = payload;
        this.retryCount = 0;
        this.status = RetryStatus.PENDING;
        this.nextRetryAt = LocalDateTime.now();
    }

    /**
     * 시도가 또 실패했다. 다음 시각을 지수 백오프로 미룬다.
     * 고정 간격으로 재시도하면 PG 장애가 길어질 때 같은 부하를 계속 실어 회복을 방해한다.
     */
    public void recordFailure(String error, int maxRetryCount) {
        this.retryCount++;
        this.lastError = error;
        if (retryCount >= maxRetryCount) {
            this.status = RetryStatus.FAILED;
            this.nextRetryAt = null;
            return;
        }
        this.nextRetryAt = LocalDateTime.now().plus(BASE_BACKOFF.multipliedBy(1L << (retryCount - 1)));
    }

    public void succeed() {
        this.status = RetryStatus.SUCCESS;
        this.nextRetryAt = null;
        this.lastError = null;
    }
}
