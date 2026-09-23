package com.kurly.common.security.activity;

import com.kurly.common.security.AuthenticatedPrincipal;

/**
 * 인증에 성공한 요청을 "활동"으로 기록한다.
 *
 * <p>기록은 <b>부가 기능</b>이다. 실패하더라도 인증 요청 자체를 막아서는 안 되므로
 * 구현체는 예외를 밖으로 던지지 않는다.
 */
@FunctionalInterface
public interface SessionActivityRecorder {

    /** 브로커가 없는 환경(테스트·기능 비활성)에서 쓰는 기본 구현. */
    SessionActivityRecorder NOOP = principal -> {
    };

    void record(AuthenticatedPrincipal principal);
}
