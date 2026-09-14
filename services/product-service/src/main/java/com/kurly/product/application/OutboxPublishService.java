package com.kurly.product.application;

import com.kurly.product.application.port.EventPublisher;
import com.kurly.product.infrastructure.entity.ProductOutbox;
import com.kurly.product.infrastructure.jpa.ProductOutboxJpaRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublishService {

    private final ProductOutboxJpaRepository productOutboxJpaRepository;
    private final EventPublisher eventPublisher;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOne(Long id) {
        productOutboxJpaRepository.findPendingByIdForUpdateSkipLocked(id).ifPresent(this::tryPublish);
    }

    @Transactional
    public void publishPending(int batchSize) {
        List<ProductOutbox> pending = productOutboxJpaRepository.findPendingForUpdateSkipLocked(batchSize);
        if (pending.isEmpty()) {
            return;
        }

        int published = 0;
        for (ProductOutbox event : pending) {
            if (tryPublish(event)) {
                published++;
            }
        }
        log.info("아웃박스 발행 완료: 대상={}, 성공={}", pending.size(), published);
    }

    private boolean tryPublish(ProductOutbox event) {
        try {
            eventPublisher.publish(event.getExchange(), event.getRoutingKey(), event.getTypeId(), event.getPayload());
            event.markPublished();
            productOutboxJpaRepository.save(event);
            return true;
        } catch (RuntimeException e) {
            log.warn("아웃박스 발행 실패. 스케줄러가 다음 주기에 재시도한다: id={}, eventId={}, typeId={}",
                    event.getId(), event.getEventId(), event.getTypeId(), e);
            return false;
        }
    }
}
