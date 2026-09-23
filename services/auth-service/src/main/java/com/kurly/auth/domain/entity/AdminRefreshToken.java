package com.kurly.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "admin_refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_admin_refresh_tokens_token",
                columnNames = "token"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false, columnDefinition = "boolean")
    private boolean revoked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 마지막 활동 시각. 유휴 세션 자동 차단 판정의 기준이다(설계서 1.6).
     * 기존 행에는 값이 없을 수 있어 {@code null}을 허용하며, 그때는 {@code createdAt}으로 판정한다.
     */
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "admin_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_admin_refresh_tokens_auth_admin")
    )
    private AuthAdmin authAdmin;

    @Builder
    private AdminRefreshToken(String token, LocalDateTime expiresAt, LocalDateTime lastUsedAt, AuthAdmin authAdmin) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.authAdmin = authAdmin;
        this.revoked = false;
        this.lastUsedAt = lastUsedAt;
    }

    /** 활동을 기록한다. 이미 더 최근 기록이 있으면 덮어쓰지 않는다. */
    public void touch(LocalDateTime usedAt) {
        if (this.lastUsedAt == null || this.lastUsedAt.isBefore(usedAt)) {
            this.lastUsedAt = usedAt;
        }
    }

    /** 판정 기준 시각. 활동 기록이 없으면 발급 시각을 쓴다. */
    public LocalDateTime lastActivityAt() {
        return lastUsedAt != null ? lastUsedAt : createdAt;
    }

    /** 로그아웃·rotation·재사용 감지 시 무효화한다. */
    public void revoke() {
        this.revoked = true;
    }
}
