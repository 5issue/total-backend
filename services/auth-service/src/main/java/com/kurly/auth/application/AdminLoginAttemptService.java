package com.kurly.auth.application;

import com.kurly.auth.domain.repository.AuthAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 관리자 인증 실패 횟수와 일시 잠금을 관리한다(인증인가_설계서 1.7, 안전성 확보조치 기준 제5조).
 *
 * <p><b>BCrypt 검증과 분리된 별도 빈인 이유</b>: 카운터 갱신 트랜잭션이 비밀번호 해시 검증을
 * 감싸면, 같은 계정을 노린 요청들이 행 잠금을 두고 직렬화되며 그 시간만큼 커넥션을 점유한다.
 * 커넥션 풀은 서비스 전체 공용이라, 계정 하나를 노린 공격이 다른 엔드포인트까지 마비시킬 수 있다.
 * 따라서 해시 검증은 트랜잭션 밖에서 하고 여기서는 짧은 갱신만 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminLoginAttemptService {

    /** 잠금까지 허용하는 연속 실패 횟수. 정책 확정 전 잠정값이다. */
    static final int MAX_RETRY_COUNT = 5;

    /** 잠금 지속 시간. 팀 확정값(10분). 경과하면 자동 해제된다. */
    static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final AuthAdminRepository authAdminRepository;

    @Transactional
    public void recordFailure(Long adminId) {
        authAdminRepository.increaseRetryCount(adminId);

        authAdminRepository.findById(adminId).ifPresent(admin -> {
            if (admin.getRetryCount() >= MAX_RETRY_COUNT) {
                authAdminRepository.lockUntil(adminId, LocalDateTime.now().plus(LOCK_DURATION));
                log.warn("연속 인증 실패로 관리자 계정을 잠금: authAdminId={}, retryCount={}, 잠금 {}분",
                        adminId, admin.getRetryCount(), LOCK_DURATION.toMinutes());
            }
        });
    }

    @Transactional
    public void recordSuccess(Long adminId) {
        authAdminRepository.clearLoginFailures(adminId);
    }
}
