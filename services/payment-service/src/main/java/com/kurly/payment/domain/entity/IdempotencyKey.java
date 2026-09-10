package com.kurly.payment.domain.entity;

import com.kurly.payment.domain.enums.IdempotencyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 결제 멱등키.
 *
 * <p>유일성을 {@code (userId, idempotencyKey)}로 잡는다. 키만으로 잡으면 다른 사용자가 우연히 같은
 * UUID를 보냈을 때 남의 결제 응답(금액·영수증)을 그대로 돌려받는다.
 *
 * <p>처리 <b>전에</b> {@code IN_PROGRESS}로 먼저 저장한다. 유니크 제약 위반이 곧 "이미 진행 중"의
 * 신호가 되어, 별도 잠금 없이 중복 요청을 가려낼 수 있다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_idempotency_keys_user_key",
                columnNames = {"user_id", "idempotency_key"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    /** 같은 키를 다른 엔드포인트에 재사용했는지 판별한다. */
    @Column(name = "request_path", nullable = false, length = 255)
    private String requestPath;

    /**
     * 요청 본문의 SHA-256 지문.
     *
     * <p>본문 원문을 저장해 비교하지 않는다. JSON 컬럼은 MySQL이 키 순서와 공백을 정규화해
     * 되읽은 값이 직렬화 원문과 절대 일치하지 않으므로, 원문 비교는 항상 "다른 본문"으로 판정된다.
     * 결제 본문에는 PG 인증 토큰이 들어 있어 원문을 남기지 않는 편이 안전하기도 하다.
     */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private IdempotencyStatus status;

    /** 완료된 응답의 HTTP 상태. 재요청 시 본문과 함께 그대로 재생한다. */
    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body")
    private String responseBody;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private IdempotencyKey(Long userId, String idempotencyKey, String requestPath, String requestFingerprint) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestFingerprint = requestFingerprint;
        this.status = IdempotencyStatus.IN_PROGRESS;
    }

    public void complete(int responseStatus, String responseBody) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = IdempotencyStatus.COMPLETED;
    }

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    /** 같은 키가 다른 엔드포인트나 다른 본문으로 재사용됐는지. 클라이언트 오류이므로 거부한다. */
    public boolean conflictsWith(String requestPath, String requestFingerprint) {
        return !this.requestPath.equals(requestPath)
                || !this.requestFingerprint.equals(requestFingerprint);
    }
}
