package com.kurly.user.domain.repository;

import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;

import java.util.Optional;

public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /** {@code sync-profile}의 멱등 조회. 소셜 식별자로 기존 회원을 찾는다. */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
