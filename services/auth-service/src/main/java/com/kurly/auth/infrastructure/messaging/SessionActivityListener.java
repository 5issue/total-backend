package com.kurly.auth.infrastructure.messaging;

import com.kurly.auth.application.SessionActivityService;
import com.kurly.common.security.Role;
import com.kurly.common.security.activity.SessionActivityChannels;
import com.kurly.common.security.activity.UserActivityEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 활동 이벤트를 받아 모았다가 주기적으로 반영한다(세션활동_이벤트_통신명세 5장).
 *
 * <p><b>수신 즉시 쓰지 않고 병합한다.</b> 같은 사용자의 이벤트가 여러 서비스·여러 파드에서
 * 중복으로 오기 때문이다. {@code (역할, userId)}별 최신 시각만 남기면 중복과 순서 뒤바뀜이
 * 모두 무해해진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionActivityListener {

    private final SessionActivityService sessionActivityService;
    private final SessionActivityLag sessionActivityLag;
    private final AmqpAdmin amqpAdmin;

    /** {@code (역할, userId)} → 관측된 최신 활동 시각. */
    private final Map<ActivityKey, Instant> pending = new ConcurrentHashMap<>();

    @RabbitListener(queues = SessionActivityChannels.QUEUE)
    public void onActivity(UserActivityEvent event) {
        if (event == null || event.userId() == null || event.occurredAt() == null) {
            // 스키마가 어긋난 메시지다. 재큐하면 소비 전체가 막히므로 버린다.
            log.warn("형식이 올바르지 않은 활동 이벤트를 버린다: {}", event);
            return;
        }
        Role role;
        try {
            role = Role.valueOf(event.role());
        } catch (IllegalArgumentException | NullPointerException e) {
            log.warn("알 수 없는 역할의 활동 이벤트를 버린다: role={}", event.role());
            return;
        }
        // 늦게 도착한 과거 이벤트는 최신 기록을 덮어쓰지 않는다.
        pending.merge(new ActivityKey(role, event.userId()), event.occurredAt(),
                (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
    }

    @Scheduled(fixedDelayString = "${auth.idle-timeout.flush-interval-ms:5000}")
    public void flush() {
        if (pending.isEmpty()) {
            sessionActivityLag.onFlush(Instant.now(), backlog());
            return;
        }

        Map<ActivityKey, Instant> batch = new HashMap<>();
        // 반복 중에 들어오는 이벤트를 잃지 않도록 키별로 꺼낸다.
        for (ActivityKey key : Set.copyOf(pending.keySet())) {
            Instant occurredAt = pending.remove(key);
            if (occurredAt != null) {
                batch.put(key, occurredAt);
            }
        }

        // 지연 지표는 "가장 최근에 반영한 활동 시각" 기준이다. 밀려 있으면 이 값이 과거에 머문다.
        Instant processedUpTo = Instant.EPOCH;
        for (Map.Entry<ActivityKey, Instant> entry : batch.entrySet()) {
            ActivityKey key = entry.getKey();
            try {
                sessionActivityService.touch(key.role(), key.userId(), entry.getValue());
            } catch (Exception e) {
                // 한 건의 실패가 나머지 반영을 막지 않게 한다. 다음 활동 때 다시 기록된다.
                log.warn("활동 반영 실패: role={}, userId={}", key.role(), key.userId(), e);
            }
            if (entry.getValue().isAfter(processedUpTo)) {
                processedUpTo = entry.getValue();
            }
        }
        sessionActivityLag.onFlush(processedUpTo, backlog());
    }

    /** 큐에 남은 대기 메시지 수. 조회에 실패하면 적체 없음으로 본다(판정을 막지 않는다). */
    private long backlog() {
        try {
            QueueInformation info = amqpAdmin.getQueueInfo(SessionActivityChannels.QUEUE);
            return info == null ? 0L : info.getMessageCount();
        } catch (Exception e) {
            log.debug("큐 상태 조회 실패", e);
            return 0;
        }
    }

    private record ActivityKey(Role role, Long userId) {
    }
}
