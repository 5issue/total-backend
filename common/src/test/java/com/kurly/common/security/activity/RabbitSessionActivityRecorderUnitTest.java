package com.kurly.common.security.activity;

import com.github.benmanes.caffeine.cache.Ticker;
import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("세션 활동 이벤트 발행")
class RabbitSessionActivityRecorderUnitTest {

    private static final Duration WINDOW = Duration.ofSeconds(60);

    @Mock RabbitTemplate rabbitTemplate;

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;

    /** 발행은 전용 실행기로 넘어간다. 테스트에서는 같은 스레드에서 돌려 결정적으로 만든다. */
    private RabbitSessionActivityRecorder recorder() {
        return new RabbitSessionActivityRecorder(rabbitTemplate, "order-service", WINDOW, ticker, Runnable::run);
    }

    private void advance(Duration amount) {
        nanos.addAndGet(amount.toNanos());
    }

    private AuthenticatedPrincipal principal(Long userId, Role role) {
        return new AuthenticatedPrincipal(userId, role);
    }

    @Test
    void 디바운스_창_안의_반복_요청은_한_번만_발행한다() {
        RabbitSessionActivityRecorder recorder = recorder();

        for (int i = 0; i < 100; i++) {
            recorder.record(principal(1001L, Role.USER));
        }

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(SessionActivityChannels.EXCHANGE), eq(SessionActivityChannels.ROUTING_KEY), any(Object.class));
    }

    @Test
    void 창이_지나면_다시_발행한다() {
        RabbitSessionActivityRecorder recorder = recorder();

        recorder.record(principal(1001L, Role.USER));
        advance(WINDOW.plusSeconds(1));
        recorder.record(principal(1001L, Role.USER));

        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(SessionActivityChannels.EXCHANGE), eq(SessionActivityChannels.ROUTING_KEY), any(Object.class));
    }

    @Test
    void 역할이_다르면_별개로_집계한다() {
        // USER와 ADMIN은 서로 다른 테이블의 id라 값이 겹칠 수 있다.
        RabbitSessionActivityRecorder recorder = recorder();

        recorder.record(principal(1L, Role.USER));
        recorder.record(principal(1L, Role.ADMIN));

        verify(rabbitTemplate, times(2)).convertAndSend(
                eq(SessionActivityChannels.EXCHANGE), eq(SessionActivityChannels.ROUTING_KEY), any(Object.class));
    }

    @Test
    void 발행에_실패해도_예외를_밖으로_내보내지_않는다() {
        // 활동 기록 때문에 인증 요청이 실패하면 안 된다.
        doThrow(new RuntimeException("broker down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatCode(() -> recorder().record(principal(1001L, Role.USER)))
                .doesNotThrowAnyException();
    }

    @Test
    void 발행_메시지는_식별정보만_담는다() {
        recorder().record(principal(1001L, Role.USER));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(any(String.class), any(String.class), payload.capture());

        assertThat(payload.getValue()).isInstanceOf(UserActivityEvent.class);
        UserActivityEvent event = (UserActivityEvent) payload.getValue();
        assertThat(event.userId()).isEqualTo(1001L);
        assertThat(event.role()).isEqualTo("USER");
        assertThat(event.service()).isEqualTo("order-service");
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    void 주체가_없으면_발행하지_않는다() {
        recorder().record(null);
        recorder().record(new AuthenticatedPrincipal(null, Role.USER));

        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void 발행이_요청_스레드를_막지_않는다() throws Exception {
        // 브로커에 연결할 수 없으면 convertAndSend가 connection timeout까지 호출 스레드를 붙잡는다.
        // 그 대기가 요청 스레드에서 일어나면 브로커 장애가 곧바로 인증 지연이 된다.
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            publishing.countDown();
            release.await(5, TimeUnit.SECONDS);   // 브로커가 막힌 상황을 흉내 낸다
            return null;
        }).when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        try (RabbitSessionActivityRecorder recorder =
                     new RabbitSessionActivityRecorder(rabbitTemplate, "order-service", WINDOW)) {
            long startedAt = System.nanoTime();
            recorder.record(principal(1001L, Role.USER));
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

            assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();  // 발행은 다른 스레드에서 진행 중
            assertThat(elapsed).isLessThan(Duration.ofMillis(500));      // 호출은 즉시 반환
            release.countDown();
        }
    }
}
