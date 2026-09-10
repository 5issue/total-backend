package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.PaymentStatus;
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

/**
 * 결제.
 *
 * <p>주문 도메인은 별도 DB에 있어 {@code orderId}는 논리적 참조값이다. {@code userId}는 토큰의
 * {@code sub}와 비교하는 소유권 검사 기준이며, ERD 검토 과정에서 추가됐다.
 *
 * <p><b>금액은 원 단위 정수다.</b> 소수점을 허용하면 PG가 절사·반올림했을 때 승인 금액과 저장 금액이
 * 어긋나 정산 대사가 실패한다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    /** 결제 소유자. 모든 조회에 이 값을 함께 걸어 타인 리소스 접근을 막는다. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** PG가 발급한 결제 식별자. 승인 전에는 없다. */
    @Column(name = "payment_key", length = 255)
    private String paymentKey;

    /**
     * 결제 수단. PG가 정하는 값이라 enum으로 좁히지 않는다.
     * 우리가 아는 값만 허용하면 PG가 수단을 추가할 때 승인 응답을 저장하지 못한다.
     */
    @Column(name = "method", length = 50)
    private String method;

    @Column(name = "total_amount", nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * PG 영수증 주소. 승인 응답으로 확정된다.
     * 조회 때마다 PG에 묻지 않는 이유는 읽기 경로에 외부 의존과 지연을 들이지 않기 위함이다.
     */
    @Column(name = "receipt_url", length = 255)
    private String receiptUrl;

    /** 결제 요청 시각. 행 생성 시각과 같으므로 auditing이 채운다. */
    @CreatedDate
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    /**
     * PG 대사를 마친 시각. {@code null}이면 아직 맞춰보지 않은 건이다.
     * 대사 중에는 선점 표시로도 쓰여, 여러 인스턴스가 같은 결제를 동시에 조회하지 않게 한다.
     */
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    @Builder
    private Payment(Long orderId, Long userId, Long totalAmount) {
        // 금액 불변식은 DB CHECK로도 막지만, 잘못된 금액의 결제 객체가 아예 만들어지지 않게 한다.
        if (totalAmount == null || totalAmount <= 0) {
            throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다: " + totalAmount);
        }
        this.orderId = orderId;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = PaymentStatus.REQUESTED;
    }

    /** PG 승인 완료. 승인 응답으로 확정된 식별자·수단·영수증 주소를 함께 기록한다. */
    public void approve(String paymentKey, String method, String receiptUrl) {
        this.paymentKey = paymentKey;
        this.method = method;
        this.receiptUrl = receiptUrl;
        this.status = PaymentStatus.SUCCESS;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    /** 취소 완료. 부분 취소를 제공하지 않으므로 항상 전액이다. */
    public void cancel() {
        this.status = PaymentStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
    }

    /**
     * 소유자 대조.
     *
     * <p>동기 경로에서는 토큰의 {@code sub}와, 비동기 경로에서는 메시지가 실어 보낸 식별자와 맞춰본다.
     * 후자는 서명이 없어 인증이 아니라 <b>정합성 검증</b>이며, 불일치는 사용자 오류가 아니라
     * 발행자 버그이므로 호출부가 다르게 다뤄야 한다.
     */
    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    public boolean isCancellable() {
        return status.isCancellable();
    }

    /** 대사 대상으로 선점한다. 조회 트랜잭션 안에서 표시해 두어 다른 인스턴스가 집지 않게 한다. */
    public void claimReconciliation() {
        this.reconciledAt = LocalDateTime.now();
    }

    /**
     * 선점을 되돌린다. PG 조회나 후속 처리가 실패해 <b>결론을 내지 못했을 때</b> 부른다.
     * 되돌리지 않으면 대사되지 않은 건이 대사 완료로 남아 영영 다시 보지 않게 된다.
     */
    public void releaseReconciliation() {
        this.reconciledAt = null;
    }
}
