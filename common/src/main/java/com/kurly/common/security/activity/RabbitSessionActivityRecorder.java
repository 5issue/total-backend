package com.kurly.common.security.activity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.kurly.common.security.AuthenticatedPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 활동을 RabbitMQ로 발행한다. auth-service가 소비해 {@code last_used_at}을 갱신한다.
 *
 * <p><b>발행은 요청 스레드에서 하지 않는다.</b> {@code convertAndSend}는 컨슈머 처리를 기다리지
 * 않지만, 브로커에 연결할 수 없으면 호출 스레드에서 재연결을 시도하며 connection timeout까지
 * 멈추고, 브로커가 {@code connection.blocked} 상태면 발행 자체가 막힌다. 디바운스 창마다
 * 사용자당 한 번은 이 경로를 타므로, 그대로 두면 <b>브로커 장애가 전 서비스의 인증 지연</b>이 된다.
 * {@code try/catch}는 예외만 삼킬 뿐 대기 시간을 줄이지 못한다.
 *
 * <p>그래서 전용 실행기로 넘긴다. 큐가 가득 차면 <b>이벤트를 버린다</b>. 활동 기록은 유실돼도
 * 갱신 시 동기 기록으로 degrade할 뿐이라, 요청을 지연시키는 것보다 버리는 편이 낫다.
 *
 * <p><b>디바운스는 파드 로컬 메모리로 한다.</b> 공유 저장소를 쓰면 매 인증 요청마다 네트워크
 * 왕복이 임계 경로에 들어간다. 소비자가 {@code userId}별 최신 시각만 취해 병합하므로
 * 파드 간 중복은 무해하다.
 */
@Slf4j
public class RabbitSessionActivityRecorder implements SessionActivityRecorder, AutoCloseable {

    /**
     * 디바운스 키 저장소. 상한을 두어 메모리가 무한히 늘지 않게 한다.
     * 상한을 넘으면 오래된 항목이 밀려나고, 그 사용자의 이벤트가 한 번 더 나갈 뿐이다.
     */
    private static final int MAX_TRACKED_PRINCIPALS = 100_000;

    /** 발행 대기 상한. 넘으면 버린다. 브로커가 막혀 있을 때 메모리가 늘지 않게 하는 장치다. */
    private static final int PUBLISH_QUEUE_CAPACITY = 1_000;

    /** 브로커가 막히면 이 수만큼의 스레드만 대기한다. 요청 스레드는 영향을 받지 않는다. */
    private static final int PUBLISH_THREADS = 2;

    private final RabbitTemplate rabbitTemplate;
    private final String serviceName;
    private final Cache<String, Boolean> debounce;
    private final Executor publisher;
    private final Runnable shutdown;

    public RabbitSessionActivityRecorder(RabbitTemplate rabbitTemplate, String serviceName, Duration window) {
        this(rabbitTemplate, serviceName, window, Ticker.systemTicker());
    }

    /** 디바운스 만료와 발행 시점을 결정적으로 검증하기 위해 시계·실행기를 주입받는 생성자. */
    RabbitSessionActivityRecorder(RabbitTemplate rabbitTemplate, String serviceName,
                                  Duration window, Ticker ticker, Executor publishExecutor) {
        this.rabbitTemplate = rabbitTemplate;
        this.serviceName = serviceName;
        this.debounce = newDebounceCache(window, ticker);
        this.publisher = publishExecutor;
        this.shutdown = () -> {
        };
    }

    private RabbitSessionActivityRecorder(RabbitTemplate rabbitTemplate, String serviceName,
                                          Duration window, Ticker ticker) {
        this.rabbitTemplate = rabbitTemplate;
        this.serviceName = serviceName;
        this.debounce = newDebounceCache(window, ticker);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                PUBLISH_THREADS, PUBLISH_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(PUBLISH_QUEUE_CAPACITY),
                runnable -> {
                    Thread thread = new Thread(runnable, "session-activity-publisher");
                    thread.setDaemon(true);
                    return thread;
                },
                // 가득 차면 조용히 버린다. 호출 스레드에서 대신 실행(CallerRuns)하면
                // 요청 스레드가 막혀 이 클래스가 막으려는 상황이 그대로 재현된다.
                new ThreadPoolExecutor.DiscardPolicy());
        this.publisher = pool;
        this.shutdown = pool::shutdownNow;
    }

    private static Cache<String, Boolean> newDebounceCache(Duration window, Ticker ticker) {
        return Caffeine.newBuilder()
                .expireAfterWrite(window)
                .maximumSize(MAX_TRACKED_PRINCIPALS)
                .ticker(ticker)
                .build();
    }

    @Override
    public void record(AuthenticatedPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            return;
        }
        // USER와 ADMIN은 서로 다른 테이블의 id라 값이 겹칠 수 있다. 역할을 키에 포함한다.
        String key = principal.role() + ":" + principal.userId();
        if (debounce.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }

        UserActivityEvent event = new UserActivityEvent(
                principal.userId(), principal.role().name(), Instant.now(), serviceName);
        publisher.execute(() -> publish(event));
    }

    private void publish(UserActivityEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    SessionActivityChannels.EXCHANGE, SessionActivityChannels.ROUTING_KEY, event);
        } catch (Exception e) {
            // 다음 디바운스 창에서 다시 시도된다.
            log.debug("세션 활동 이벤트 발행 실패: userId={}", event.userId(), e);
        }
    }

    @Override
    public void close() {
        shutdown.run();
    }
}
