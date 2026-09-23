package com.kurly.common.security.activity;

import java.time.Instant;

/**
 * 사용자 활동 발생 사실. 유휴 세션 자동 차단(인증인가_설계서 1.6) 판정의 입력이다.
 *
 * <p><b>토큰 원문·개인정보를 싣지 않는다</b>(설계서 3.5 — 비동기 이벤트에는 식별정보만).
 *
 * @param userId     access token의 {@code sub}
 * @param role       갱신 대상 테이블을 가른다(USER/ADMIN)
 * @param occurredAt 활동 발생 시각. 발행 시각이 아니라 요청을 처리한 시각이다
 * @param service    발행 서비스. 판정에 쓰지 않고 관측용이다
 */
public record UserActivityEvent(Long userId, String role, Instant occurredAt, String service) {
}
