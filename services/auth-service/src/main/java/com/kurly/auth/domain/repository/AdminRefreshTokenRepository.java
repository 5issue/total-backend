package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AdminRefreshToken;

import java.util.List;
import java.util.Optional;

public interface AdminRefreshTokenRepository {

    AdminRefreshToken save(AdminRefreshToken adminRefreshToken);

    Optional<AdminRefreshToken> findById(Long id);

    /** 갱신 요청의 refresh token 해시로 조회한다. */
    Optional<AdminRefreshToken> findByToken(String token);

    /** 재사용 감지 시 해당 관리자의 세션을 일괄 무효화하기 위해 조회한다. */
    List<AdminRefreshToken> findAllByAuthAdminId(Long authAdminId);

    /** 명시적 로그아웃 시 해당 관리자의 세션을 제거한다(인증인가_설계서 1.6). */
    void deleteAllByAuthAdminAdminId(Long adminId);
}
