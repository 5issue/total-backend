package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.AdminRefreshToken;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminRefreshTokenJpaRepository
        extends JpaRepository<AdminRefreshToken, Long>, AdminRefreshTokenRepository {

    @Override
    <S extends AdminRefreshToken> S save(S entity);

    @Override
    Optional<AdminRefreshToken> findById(Long id);

    /** {@code clearAutomatically}를 켜지 않는 이유는 {@link UserRefreshTokenJpaRepository}와 같다. */
    @Override
    @Modifying
    @Query("update AdminRefreshToken t set t.revoked = true where t.token = :token and t.revoked = false")
    int revokeIfActive(@Param("token") String token);
}
