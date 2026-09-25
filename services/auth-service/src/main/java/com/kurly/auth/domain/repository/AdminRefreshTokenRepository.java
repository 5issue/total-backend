package com.kurly.auth.domain.repository;

import com.kurly.auth.domain.entity.AdminRefreshToken;

import java.util.List;
import java.util.Optional;

public interface AdminRefreshTokenRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 메서드가
     * 서로를 재정의하지 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간
     * "reference to save is ambiguous" 컴파일 오류가 난다.
     */
    <S extends AdminRefreshToken> S save(S adminRefreshToken);

    Optional<AdminRefreshToken> findById(Long id);

    /** 갱신 요청의 refresh token 해시로 조회한다. */
    Optional<AdminRefreshToken> findByToken(String token);

    /**
     * 아직 살아 있는 토큰만 폐기하고 갱신된 행 수를 돌려준다.
     * 동시 갱신 요청에서 승자를 DB가 정하게 한다. 0이면 호출부는 재사용으로 처리한다.
     */
    int revokeIfActive(String token);

    /** 재사용 감지 시 해당 관리자의 세션을 일괄 무효화하기 위해 조회한다. */
    List<AdminRefreshToken> findAllByAuthAdminId(Long authAdminId);

    /**
     * 활동 이벤트를 살아 있는 세션에 반영한다(세션활동_이벤트_통신명세 5-1).
     *
     * <p>폐기·만료된 세션은 건드리지 않는다. 되살리면 유휴 판정을 우회하게 된다.
     * 이미 더 최근 기록이 있으면 덮어쓰지 않아, 늦게 도착한 과거 이벤트가 무해해진다.
     *
     * @return 갱신된 행 수
     */
    int touchActiveSessions(Long userId, java.time.LocalDateTime usedAt, java.time.LocalDateTime now);

    /** 명시적 로그아웃 시 해당 관리자의 세션을 제거한다(인증인가_설계서 1.6). */
    void deleteAllByAuthAdminAdminId(Long adminId);
}
