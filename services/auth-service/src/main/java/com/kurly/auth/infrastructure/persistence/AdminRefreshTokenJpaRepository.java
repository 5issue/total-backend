package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.AdminRefreshToken;
import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRefreshTokenJpaRepository
        extends JpaRepository<AdminRefreshToken, Long>, AdminRefreshTokenRepository {
}
