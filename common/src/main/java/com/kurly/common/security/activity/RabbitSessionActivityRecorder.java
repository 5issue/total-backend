package com.kurly.common.security.activity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.kurly.common.security.AuthenticatedPrincipal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;
import java.time.Instant;

/**
 * 활동을 RabbitMQ로 발행한다. auth-service가 소비해 {@code last_used_at}을 갱신한다.
 *
 * <p><b>디바운스는 파드 로컬 메모리로 한다.</b> 공유 저장소(Redis)를 쓰면 파드 간 중복까지
 * 없앨 수 있지만, 그러려면 매 인증 요청마다 네트워크 왕복이 임계 경로에 들어간다.
 * 소비자가 {@code userId}별 최신 시각만 취해 병합하므로 <b>중복 발행은 무해하다.</b>
 * 파드 수만큼 메시지가 늘 뿐이고, 그 양은 브로커에 부담이 되지 않는다.
 *
 * <p><b>발행 실패는 삼킨다.</b> 활동 기록 때문에 인증이 실패해서는 안 된다. 브로커가 죽어도
 * 유휴 판정은 "토큰 갱신 시 동기 기록" 기준으로 degrade할 뿐이다.
 */
@Slf4j
public class RabbitSessionActivityRecorder implements SessionActivityRecorder {

    /**
     * 디바운스 키 저장소. 상한을 두어 메모리가 무한히 늘지 않게 한다.
     * 상한을 넘으면 오래된 항목이 밀려나고, 그 사용자의 이벤트가 한 번 더 나갈 뿐이다.
     */
    private static final int MAX_TRACKED_PRINCIPALS = 100_000;

    private final RabbitTemplate rabbitTemplate;
    private final String serviceName;
    private final Cache<String, Boolean> debounce;

    public RabbitSessionActivityRecorder(RabbitTemplate rabbitTemplate, String serviceName, Duration window) {
        this(rabbitTemplate, serviceName, window, Ticker.systemTicker());
    }

    /** 디바운스 만료를 결정적으로 검증하기 위해 시계를 주입받는 생성자. */
    RabbitSessionActivityRecorder(RabbitTemplate rabbitTemplate, String serviceName,
                                  Duration window, Ticker ticker) {
        this.rabbitTemplate = rabbitTemplate;
        this.serviceName = serviceName;
        this.debounce = Caffeine.newBuilder()
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

        try {
            rabbitTemplate.convertAndSend(
                    SessionActivityChannels.EXCHANGE,
                    SessionActivityChannels.ROUTING_KEY,
                    new UserActivityEvent(principal.userId(), principal.role().name(),
                            Instant.now(), serviceName));
        } catch (Exception e) {
            // 다음 디바운스 창에서 다시 시도된다. 요청은 그대로 진행한다.
            log.debug("세션 활동 이벤트 발행 실패: userId={}", principal.userId(), e);
        }
    }
}
