package com.kurly.auth.application;

import com.kurly.auth.domain.repository.AdminRefreshTokenRepository;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import com.kurly.common.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 활동 이벤트를 {@code last_used_at}에 반영한다(세션활동_이벤트_통신명세 5-1).
 *
 * <p>판정 단위는 <b>사용자</b>다. 이벤트가 단말을 식별하지 않으므로 그 사용자의 활성 세션 전체를
 * 갱신한다. 방치된 단말의 세션도 다른 기기에서의 활동으로 함께 연장되는 한계가 있으며,
 * 단말 단위 전환은 별도 과제다(명세 3-4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionActivityService {

    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;

    /**
     * <b>시스템 기본 시간대로 변환한다.</b> {@code created_at}(JPA Auditing)·{@code expires_at}
     * ({@code AuthTokenService.toLocalDateTime})이 모두 그 기준이라, 여기만 UTC로 저장하면
     * 같은 컬럼에 두 시계가 섞여 <b>시차만큼 유휴로 오판</b>한다(KST면 9시간).
     */
    @Transactional
    public void touch(Role role, Long userId, Instant occurredAt) {
        LocalDateTime usedAt = LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault());
        LocalDateTime now = LocalDateTime.now();

        int updated = switch (role) {
            case USER -> userRefreshTokenRepository.touchActiveSessions(userId, usedAt, now);
            case ADMIN -> adminRefreshTokenRepository.touchActiveSessions(userId, usedAt, now);
        };

        if (updated == 0) {
            // 이미 더 최근 기록이 있거나, 폐기·만료된 세션이다. 정상 상황이다.
            log.trace("반영할 활성 세션 없음: role={}, userId={}", role, userId);
        }
    }
}
