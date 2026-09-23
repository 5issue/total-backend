package com.kurly.auth.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
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
 * <p><b>관측 자체가 낡으면 지연을 주장하지 않는다.</b> flush가 완료되지 못하면(예: DB 잠금에
 * 걸려 반환되지 않음) snapshot이 갱신되지 않는데, 그때 마지막 값을 그대로 믿으면 지연이 무한히
 * 커져 <b>유휴 판정이 영구히 우회</b>된다. 법적 근거가 있는 통제가 조용히 꺼지는 상태다.
 * 그래서 관측이 {@code staleAfter}보다 오래되면 지연을 0으로 보고 엄격 판정으로 돌아간다.
 */
@Slf4j
public class SessionActivityLag {

    private final Duration staleAfter;
    private final Clock clock;
    private final AtomicReference<Snapshot> snapshot;

    public SessionActivityLag(Duration staleAfter) {
        this(staleAfter, Clock.systemUTC());
    }

    /** 관측 신선도 판정을 결정적으로 검증하기 위해 시계를 주입받는 생성자. */
    SessionActivityLag(Duration staleAfter, Clock clock) {
        this.staleAfter = staleAfter;
        this.clock = clock;
        this.snapshot = new AtomicReference<>(new Snapshot(clock.instant(), clock.instant(), 0L));
    }

    /** flush할 때마다 그 배치의 최신 활동 시각과 남은 적체를 기록한다. */
    public void onFlush(Instant processedUpTo, long backlog) {
        snapshot.set(new Snapshot(clock.instant(), processedUpTo, backlog));
    }

    public Duration current() {
        Snapshot current = snapshot.get();
        if (current.backlog() <= 0) {
            return Duration.ZERO;
        }

        Instant now = clock.instant();
        Duration sinceObserved = Duration.between(current.takenAt(), now);
        if (sinceObserved.compareTo(staleAfter) > 0) {
            // 컨슈머가 멈춰 관측이 갱신되지 않는 상태다. 이 값을 근거로 계속 우회하면
            // 유휴 차단이 무기한 꺼진다. 지연을 주장하지 않고 엄격 판정으로 돌아간다.
            log.error("활동 반영 관측이 낡아 지연 보정을 중단한다: 마지막 관측 {} 전", sinceObserved);
            return Duration.ZERO;
        }

        Duration lag = Duration.between(current.processedUpTo(), now);
        return lag.isNegative() ? Duration.ZERO : lag;
    }

    private record Snapshot(Instant takenAt, Instant processedUpTo, long backlog) {
    }
}
