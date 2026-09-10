package com.kurly.user.domain.repository;

import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.enums.AuthProvider;

import java.util.Optional;

public interface UserRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 선언이 서로를 재정의하지
     * 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간 모호성 오류가 난다.
     */
    <S extends User> S save(S user);

    Optional<User> findById(Long id);

    /**
     * 기본 배송지 전환을 직렬화하기 위해 회원 행을 잠근다.
     *
     * <p>배송지 행만으로는 잠글 대상이 없다 — 첫 배송지를 만들 때는 기존 행이 없기 때문이다.
     * 회원 행을 기준점으로 삼아 같은 회원의 전환이 겹치지 않게 한다.
     */
    Optional<User> findByIdForUpdate(Long id);

    /** {@code sync-profile}의 멱등 조회. 소셜 식별자로 기존 회원을 찾는다. */
    Optional<User> findByProviderAndProviderId(AuthProvider provider, String providerId);
}
