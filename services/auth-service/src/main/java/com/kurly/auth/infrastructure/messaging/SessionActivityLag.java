package com.kurly.auth.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 활동 이벤트 반영이 얼마나 뒤처져 있는지(세션활동_이벤트_통신명세 5-2).
 *
 * <p>컨슈머가 밀리면 {@code last_used_at}이 실제보다 과거에 머문다. 그 상태로 판정하면
 * <b>계속 활동 중이던 사용자를 유휴로 오판해 세션을 끊는다.</b> 되돌릴 수 없는 처분이므로
 * 판정 전에 이 지연만큼을 빼 준다.
 *
 * <p><b>적체 여부를 함께 본다.</b> 반영 시각이 오래됐다는 것만으로는 "한가해서 이벤트가 없음"과
 * "밀려서 오래된 것만 처리 중"을 구분할 수 없다. 큐에 대기 메시지가 없으면 지연은 0으로 본다.
 *
 * <p>큐 깊이는 flush 주기마다 갱신된 값을 읽는다. 갱신 요청마다 브로커에 묻지 않기 위함이다.
 */
@Slf4j
public class SessionActivityLag {

    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(new Snapshot(Instant.now(), 0L));

    /** flush할 때마다 그 배치의 최신 활동 시각과 남은 적체를 기록한다. */
    public void onFlush(Instant processedUpTo, long backlog) {
        snapshot.set(new Snapshot(processedUpTo, backlog));
    }

    public Duration current() {
        Snapshot current = snapshot.get();
        if (current.backlog() <= 0) {
            return Duration.ZERO;
        }
        Duration lag = Duration.between(current.processedUpTo(), Instant.now());
        return lag.isNegative() ? Duration.ZERO : lag;
    }

    private record Snapshot(Instant processedUpTo, long backlog) {
    }
}
