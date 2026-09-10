package com.kurly.payment.application;

import com.kurly.payment.application.port.EventPublisher;
import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 아웃박스 발행.
 *
 * <p>업무 트랜잭션은 이벤트를 <b>DB에만</b> 적재하고, 브로커로 내보내는 일은 이 워커가 맡는다.
 * 업무 트랜잭션 안에서 발행하면 DB는 커밋됐는데 발행이 실패하거나 그 반대가 되어, 결제 상태와
 * 후속 처리가 어긋난다.
 *
 * <p><b>브로커 발행을 트랜잭션 안에서 한다.</b> PG 호출을 트랜잭션 밖으로 뺀 것과 다른 판단인데,
 * 브로커는 같은 클러스터 안의 빠른 로컬 연산이고 여기서는 "발행됨"과 "PUBLISHED 기록"이 갈라지면
 * 이벤트가 유실되거나 중복되기 때문이다. 트랜잭션을 유지하면 발행 후 커밋이 실패해도 다음 주기에
 * 다시 발행된다 — 최소 1회 보장이며, 소비자는 {@code eventId}로 중복을 거른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublishService {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final EventPublisher eventPublisher;

    /**
     * 미발행 이벤트를 한 묶음 발행한다.
     *
     * @return 발행에 성공한 건수
     */
    @Transactional
    public int publishPending(int batchSize) {
        List<PaymentOutbox> pending =
                paymentOutboxRepository.findPendingForUpdateSkipLocked(batchSize);
        if (pending.isEmpty()) {
            return 0;
        }

        int published = 0;
        for (PaymentOutbox event : pending) {
            try {
                eventPublisher.publish(event.getEventId(), event.getEventType(), event.getPayload());
                event.markPublished();
                published++;
            } catch (RuntimeException e) {
                // PENDING으로 남겨 다음 주기에 다시 시도한다. 브로커 장애는 대개 일시적이다.
                // 반복 실패하는 건을 걸러낼 시도 횟수는 아직 없다(남은 과제).
                log.error("아웃박스 발행 실패. 다음 주기에 재시도한다: eventId={}, eventType={}",
                        event.getEventId(), event.getEventType(), e);
            }
        }
        log.info("아웃박스 발행 완료: 대상={}, 성공={}", pending.size(), published);
        return published;
    }
}
