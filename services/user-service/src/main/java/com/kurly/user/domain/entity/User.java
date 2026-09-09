package com.kurly.user.domain.entity;

import com.kurly.user.domain.enums.AuthProvider;
import com.kurly.user.domain.enums.UserStatus;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * 회원. 인증 자격(소셜 provider·비밀번호)은 auth-service가 소유하고,
 * 이 테이블은 개인정보(이메일·이름)를 소유한다.
 *
 * <p>{@code auth_users.user_id}가 이 id를 참조하지만 DB가 달라 FK 제약은 없다.
 * 토큰의 {@code sub}에도 이 id가 담기므로 다른 서비스의 소유권 비교 기준이 된다.
 */
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_users_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소셜 식별자. {@code sync-profile}의 멱등 키다.
     * auth-service가 재시도해도 같은 회원이 다시 만들어지지 않도록 (provider, providerId)에
     * 유니크 제약이 걸려 있다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    /** 소셜 제공자가 동의 항목에 따라 주지 않을 수 있어 필수가 아니다. */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private User(AuthProvider provider, String providerId, String email, String name) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.name = name;
        this.status = UserStatus.ACTIVE;
    }
}
