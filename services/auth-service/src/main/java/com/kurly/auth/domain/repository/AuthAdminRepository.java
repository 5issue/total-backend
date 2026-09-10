package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AuthAdmin;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthAdminRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 메서드가
     * 서로를 재정의하지 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간
     * "reference to save is ambiguous" 컴파일 오류가 난다.
     */
    <S extends AuthAdmin> S save(S authAdmin);

    Optional<AuthAdmin> findById(Long id);

    Optional<AuthAdmin> findByLoginId(String loginId);

    /**
     * 인증 실패 횟수를 1 증가시킨다.
     * 읽고-쓰기로 처리하면 동시 요청 시 갱신이 유실되어 잠금이 우회되므로 원자적으로 갱신한다.
     */
    void increaseRetryCount(Long adminId);

    /** 임계치 도달 시 잠금 해제 시각을 설정하고 실패 횟수를 초기화한다. */
    void lockUntil(Long adminId, LocalDateTime lockedUntil);

    /** 로그인 성공 시 실패 횟수와 잠금을 해제한다. */
    void clearLoginFailures(Long adminId);
}
