package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.CancelStatus;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결제 취소 이력.
 *
 * <p>취소는 실패할 수 있고, 실패해도 <b>시도했다는 사실이 남아야</b> 배치가 재시도하고 고객 문의에
 * 답할 수 있다. 그래서 결제 상태를 덮어쓰는 대신 시도마다 행을 남긴다. 부분 취소가 여러 번
 * 일어날 수 있는 것도 같은 이유다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_cancels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 같은 DB의 부모 레코드라 연관관계로 맺는다. 대부분의 조회에서 부모가 필요 없어 지연 로딩한다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    /** 취소 금액(원 단위 정수). 부분 취소를 위해 건별로 남긴다. */
    @Column(name = "cancel_amount", nullable = false)
    private Long cancelAmount;

    /** PG가 발급한 취소 식별자. 실패 시에는 없다. */
    @Column(name = "pg_cancel_key", length = 255)
    private String pgCancelKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private CancelStatus status;

    /** PG 취소 실패 사유. 재시도 판단 근거이므로 원문을 그대로 남긴다(응답에는 싣지 않는다). */
    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private PaymentCancel(Payment payment, String cancelReason, Long cancelAmount) {
        if (cancelAmount == null || cancelAmount <= 0) {
            throw new IllegalArgumentException("취소 금액은 0보다 커야 합니다: " + cancelAmount);
        }
        this.payment = payment;
        this.cancelReason = cancelReason;
        this.cancelAmount = cancelAmount;
        this.status = CancelStatus.REQUESTED;
    }

    public void succeed(String pgCancelKey) {
        this.pgCancelKey = pgCancelKey;
        this.status = CancelStatus.SUCCESS;
        this.failureReason = null;
    }

    public void fail(String failureReason) {
        this.status = CancelStatus.FAILED;
        this.failureReason = failureReason;
    }
}
