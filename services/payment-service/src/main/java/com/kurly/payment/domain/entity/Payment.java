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

    /** PG 대사를 <b>마친</b> 시각. {@code null}이면 아직 결론이 나지 않은 건이다. */
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;

    /**
     * 대사 선점 만료 시각.
     *
     * <p>완료 표시와 <b>반드시 분리해야 한다.</b> 하나로 겸하면 선점 직후 프로세스가 죽었을 때
     * 해제 코드가 실행되지 않아 그 결제가 영원히 대사 대상에서 빠진다. 임대는 시간이 지나면
     * 저절로 풀리므로 죽은 워커의 선점을 다른 워커가 회수할 수 있다.
     */
    @Column(name = "reconcile_claimed_until")
    private LocalDateTime reconcileClaimedUntil;

    /**
     * 주문 서비스 인계 완료 시각.
     *
     * <p>승인 기록과 주문 인계는 다른 트랜잭션이다. 그 사이에 죽으면 "결제는 성공했는데 주문은
     * 모르는" 상태가 남는데, 이 값이 없으면 그 상태를 알아볼 방법이 없다.
     */
    @Column(name = "order_notified_at")
    private LocalDateTime orderNotifiedAt;

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

    /**
     * 대사 대상으로 선점한다. 조회 트랜잭션 안에서 임대를 걸어 다른 인스턴스가 집지 않게 한다.
     * 임대가 만료되면 저절로 풀리므로, 워커가 죽어도 그 건이 영구히 묻히지 않는다.
     */
    public void leaseReconciliation(java.time.Duration lease) {
        this.reconcileClaimedUntil = LocalDateTime.now().plus(lease);
    }

    /**
     * 선점을 되돌린다. PG 조회나 후속 처리가 실패해 <b>결론을 내지 못했을 때</b> 부른다.
     * 임대만 풀고 완료 표시는 남기지 않아 다음 주기가 곧바로 이어받는다.
     */
    public void releaseReconciliation() {
        this.reconcileClaimedUntil = null;
    }

    /** 대사 결론이 났다. 완료 시각을 남기고 임대를 푼다. */
    public void completeReconciliation() {
        this.reconciledAt = LocalDateTime.now();
        this.reconcileClaimedUntil = null;
    }

    /** 주문 서비스가 결제 완료를 받아들였다. */
    public void markOrderNotified() {
        this.orderNotifiedAt = LocalDateTime.now();
    }

    /** 승인은 됐는데 주문이 아직 모르는 상태인가. 대사가 회수해야 할 건이다. */
    public boolean isApprovedButNotHandedOver() {
        return status == PaymentStatus.SUCCESS && orderNotifiedAt == null;
    }
}
