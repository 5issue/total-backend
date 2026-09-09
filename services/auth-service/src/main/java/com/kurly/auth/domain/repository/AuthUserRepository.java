package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AuthUser;
import com.kurly.auth.domain.enums.AuthProvider;

import java.util.Optional;

public interface AuthUserRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 메서드가
     * 서로를 재정의하지 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간
     * "reference to save is ambiguous" 컴파일 오류가 난다.
     */
    <S extends AuthUser> S save(S authUser);

    Optional<AuthUser> findById(Long id);

    Optional<AuthUser> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
