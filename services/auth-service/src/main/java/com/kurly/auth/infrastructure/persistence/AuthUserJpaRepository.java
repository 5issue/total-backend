package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.repository.AuthUserRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserJpaRepository extends JpaRepository<AuthUser, Long>, AuthUserRepository {
}
