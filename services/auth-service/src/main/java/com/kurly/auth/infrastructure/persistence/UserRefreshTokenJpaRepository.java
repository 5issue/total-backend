package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRefreshTokenJpaRepository
        extends JpaRepository<UserRefreshToken, Long>, UserRefreshTokenRepository {
}
