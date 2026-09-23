package com.kurly.auth.infrastructure.messaging;

import com.kurly.auth.application.SessionActivityService;
import com.kurly.common.security.Role;
import com.kurly.common.security.activity.UserActivityEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.core.AmqpAdmin;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 활동 이벤트 수신·병합(세션활동_이벤트_통신명세 5장).
 *
 * <p>같은 사용자의 이벤트가 여러 서비스·여러 파드에서 중복으로 온다. 병합이 그 중복과
 * 순서 뒤바뀜을 모두 무해하게 만든다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("세션 활동 이벤트 수신")
class SessionActivityListenerUnitTest {

    @Mock SessionActivityService sessionActivityService;
    @Mock AmqpAdmin amqpAdmin;
    @org.mockito.Spy SessionActivityLag sessionActivityLag = new SessionActivityLag();

    @InjectMocks SessionActivityListener listener;

    private UserActivityEvent event(Long userId, String role, Instant at) {
        return new UserActivityEvent(userId, role, at, "order-service");
    }

    @Test
    void 같은_사용자의_중복_이벤트는_한_번만_반영한다() {
        Instant now = Instant.now();
        for (int i = 0; i < 50; i++) {
            listener.onActivity(event(1001L, "USER", now));
        }

        listener.flush();

        verify(sessionActivityService, times(1)).touch(eq(Role.USER), eq(1001L), any(Instant.class));
    }

    @Test
    void 늦게_도착한_과거_이벤트는_최신값을_덮지_않는다() {
        Instant recent = Instant.now();
        listener.onActivity(event(1001L, "USER", recent));
        listener.onActivity(event(1001L, "USER", recent.minusSeconds(600)));

        listener.flush();

        verify(sessionActivityService).touch(Role.USER, 1001L, recent);
    }

    @Test
    void 서로_다른_사용자는_각각_반영한다() {
        Instant now = Instant.now();
        listener.onActivity(event(1001L, "USER", now));
        listener.onActivity(event(9999L, "ADMIN", now));

        listener.flush();

        verify(sessionActivityService).touch(Role.USER, 1001L, now);
        verify(sessionActivityService).touch(Role.ADMIN, 9999L, now);
    }

    @Test
    void 형식이_어긋난_이벤트는_버린다() {
        // 재큐하면 잘못된 메시지 하나가 소비 전체를 막는다.
        listener.onActivity(null);
        listener.onActivity(event(null, "USER", Instant.now()));
        listener.onActivity(event(1001L, "USER", null));
        listener.onActivity(event(1001L, "SUPERUSER", Instant.now()));
        listener.onActivity(event(1001L, null, Instant.now()));

        listener.flush();

        verify(sessionActivityService, never()).touch(any(), any(), any());
    }

    @Test
    void 한_건의_반영_실패가_나머지를_막지_않는다() {
        Instant now = Instant.now();
        willThrow(new RuntimeException("DB 오류")).given(sessionActivityService)
                .touch(eq(Role.USER), eq(1001L), any(Instant.class));
        listener.onActivity(event(1001L, "USER", now));
        listener.onActivity(event(1002L, "USER", now));

        assertThatCode(listener::flush).doesNotThrowAnyException();

        verify(sessionActivityService).touch(Role.USER, 1002L, now);
    }

    @Test
    void flush_후에는_같은_이벤트가_다시_반영되지_않는다() {
        listener.onActivity(event(1001L, "USER", Instant.now()));

        listener.flush();
        listener.flush();

        verify(sessionActivityService, times(1)).touch(eq(Role.USER), eq(1001L), any(Instant.class));
    }

    @Test
    void 반영할_것이_없어도_지연_지표는_갱신한다() {
        assertThatCode(listener::flush).doesNotThrowAnyException();
        verify(sessionActivityLag).onFlush(any(Instant.class), eq(0L));
    }

    @Test
    void 큐_상태_조회에_실패해도_반영은_계속한다() {
        // 브로커 조회 실패로 판정 경로가 멈추면 안 된다.
        willThrow(new RuntimeException("broker down")).given(amqpAdmin).getQueueInfo(any());
        listener.onActivity(event(1001L, "USER", Instant.now()));

        assertThatCode(listener::flush).doesNotThrowAnyException();

        verify(sessionActivityService).touch(eq(Role.USER), eq(1001L), any(Instant.class));
    }
}
