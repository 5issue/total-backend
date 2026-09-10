package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.enums.AuthProvider;

import java.util.Optional;

public interface AuthUserRepository {

    AuthUser save(AuthUser authUser);

    Optional<AuthUser> findById(Long id);

    Optional<AuthUser> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
