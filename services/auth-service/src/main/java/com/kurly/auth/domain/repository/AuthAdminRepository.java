package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AuthAdmin;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthAdminRepository {

    AuthAdmin save(AuthAdmin authAdmin);

    Optional<AuthAdmin> findById(Long id);

    Optional<AuthAdmin> findByLoginId(String loginId);

    /**
     * 인증 실패 횟수를 1 증가시킨다.
     * 읽고-쓰기로 처리하면 동시 요청 시 갱신이 유실되어 잠금이 우회되므로 원자적으로 갱신한다.
     */
    void increaseRetryCount(Long adminId);

    /** 임계치 도달 시 잠금 해제 시각을 설정한다. */
    void lockUntil(Long adminId, LocalDateTime lockedUntil);

    /** 로그인 성공 시 실패 횟수와 잠금을 해제한다. */
    void clearLoginFailures(Long adminId);
}
