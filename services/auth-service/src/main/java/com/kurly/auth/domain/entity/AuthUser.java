package com.kurly.auth.domain.entity;

import com.kurly.auth.domain.enums.AuthProvider;
import com.kurly.auth.domain.enums.UserStatus;
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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "auth_users",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_users_provider_provider_id",
                columnNames = {"provider", "provider_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "provider", nullable = false, length = 20)
    private AuthProvider provider;

    @Column(name = "provider_id", nullable = false, length = 255)
    private String providerId;

    /**
     * 회원 도메인 {@code Users.id} 참조값. <b>DB가 다르므로 FK 제약은 걸지 않는다.</b>
     *
     * <p>토큰의 {@code sub}에 이 값을 담는다. 다른 서비스의 리소스는 회원 도메인 id를 소유자로
     * 기록하므로(예: {@code delivery_addresses.user_id}), 인증 도메인 id를 담으면
     * 소유권 비교(인증인가_설계서 2.2)가 어긋난다.
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Builder
    private AuthUser(AuthProvider provider, String providerId, Long userId) {
        this.provider = provider;
        this.providerId = providerId;
        this.userId = userId;
        this.status = UserStatus.ACTIVE;
    }
}
