package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentOutbox;

import java.util.List;

public interface PaymentOutboxRepository {

    <S extends PaymentOutbox> S save(S paymentOutbox);

    /**
     * 발행 워커가 집어갈 대상. 오래된 순으로 가져와 발행 순서를 최대한 보존한다.
     *
     * <p><b>SKIP LOCKED로 잠근다.</b> 여러 인스턴스가 같은 스케줄러를 돌리므로, 잠그지 않으면
     * 같은 이벤트를 여러 파드가 동시에 발행한다. 소비자가 event_id로 걸러내긴 하지만
     * 브로커와 소비자에 그만큼 헛부하가 실린다. 잠긴 행은 건너뛰어 서로 다른 묶음을 가져간다.
     */
    List<PaymentOutbox> findPendingForUpdateSkipLocked(int limit);
}
