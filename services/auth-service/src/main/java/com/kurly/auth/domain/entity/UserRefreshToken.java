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
        name = "user_refresh_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_refresh_tokens_token",
                columnNames = "token"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * refresh token 원본이 아니라 <b>해시값</b>을 저장한다.
     * 인증인가_설계서 1.5 — DB 유출 시에도 토큰을 재사용할 수 없어야 한다.
     */
    @Column(name = "token", nullable = false, length = 255)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_revoked", nullable = false, columnDefinition = "boolean")
    private boolean revoked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "auth_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_refresh_tokens_auth_user")
    )
    private AuthUser authUser;

    @Builder
    private UserRefreshToken(String token, LocalDateTime expiresAt, AuthUser authUser) {
        this.token = token;
        this.expiresAt = expiresAt;
        this.authUser = authUser;
        this.revoked = false;
    }

    /** 로그아웃·rotation·재사용 감지 시 무효화한다. */
    public void revoke() {
        this.revoked = true;
    }
}
