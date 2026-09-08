package com.kurly.auth.domain.entity;

import com.kurly.auth.domain.enums.AdminStatus;
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

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "auth_admins",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_auth_admins_login_id",
                columnNames = "login_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    /**
     * 회원 도메인 {@code Admins.id} 참조값. <b>DB가 다르므로 FK 제약은 걸지 않는다.</b>
     * 토큰의 {@code sub}에 이 값을 담는다.
     */
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private AdminStatus status;

    /** 연속 인증 실패 횟수. 로그인 성공 시 0으로 초기화된다. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /**
     * 일시 잠금 해제 시각. {@code null}이거나 과거면 잠금이 아니다.
     * 영구 잠금 대신 시간 기반 자동 해제를 쓰는 이유는, 공격자가 관리자 아이디만 알면
     * 일부러 실패시켜 정상 관리자를 묶어둘 수 있기 때문이다(계정 잠금 자체가 DoS 수단이 된다).
     */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Builder
    private AuthAdmin(String loginId, String password, Long adminId) {
        this.loginId = loginId;
        this.password = password;
        this.adminId = adminId;
        this.status = AdminStatus.ACTIVE;
        this.retryCount = 0;
    }

    /** 잠금 여부. 만료된 잠금은 별도 해제 작업 없이 자연히 풀린다. */
    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isDisabled() {
        return status == AdminStatus.DISABLED;
    }
}
