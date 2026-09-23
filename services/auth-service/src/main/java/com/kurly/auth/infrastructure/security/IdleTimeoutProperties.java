package com.kurly.auth.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 유휴 세션 자동 차단 설정(인증인가_설계서 1.6 — user 30분 / admin 15분).
 *
 * <p><b>기본값은 비활성이다.</b> 판정 기준인 {@code last_used_at}은 각 서비스가 보내는 활동
 * 이벤트로 채워지는데, 그 경로가 붙기 전에 켜면 "마지막 갱신 시각"만으로 판정하게 된다.
 * 그 경우 access token TTL이 유휴 한도보다 짧지 않으면 <b>활동 중인 사용자가 상시 차단</b>된다.
 * 이벤트 경로를 붙인 뒤 켠다.
 *
 * @param enabled 판정 수행 여부
 * @param user    고객 유휴 한도
 * @param admin   관리자 유휴 한도. 더 민감한 역할이라 짧게 둔다
 */
@ConfigurationProperties(prefix = "auth.idle-timeout")
public record IdleTimeoutProperties(
        boolean enabled,
        Duration user,
        Duration admin
) {
}
