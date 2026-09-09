package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.UserRefreshToken;

import java.util.List;
import java.util.Optional;

public interface UserRefreshTokenRepository {

    UserRefreshToken save(UserRefreshToken userRefreshToken);

    Optional<UserRefreshToken> findById(Long id);

    /** 갱신 요청의 refresh token 해시로 조회한다. */
    Optional<UserRefreshToken> findByToken(String token);

    /**
     * 아직 살아 있는 토큰만 폐기하고 갱신된 행 수를 돌려준다.
     *
     * <p>조회 후 폐기로 처리하면 동시 요청이 모두 "폐기되지 않음"을 보고 각자 새 토큰을 발급받아
     * 재사용 감지가 무력화된다. 폐기 여부 확인과 폐기를 한 문장으로 묶어 DB가 승자를 정하게 한다.
     * 0이면 경합에서 졌다는 뜻이므로 호출부는 재사용으로 처리한다.
     */
    int revokeIfActive(String token);

    /** 재사용 감지 시 해당 회원의 세션을 일괄 무효화하기 위해 조회한다. */
    List<UserRefreshToken> findAllByAuthUserId(Long authUserId);

    /** 명시적 로그아웃 시 해당 회원의 세션을 제거한다(인증인가_설계서 1.6). */
    void deleteAllByAuthUserUserId(Long userId);
}
