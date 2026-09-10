package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 승인은 됐는데 그 결제를 살려둘 수 없을 때 되돌리는 보상 취소.
 *
 * <p>승인 직후 경로({@link PaymentCheckoutService})와 대사 배치({@link PaymentReconciliationService})가
 * 같은 상황에 놓인다. 어느 쪽이든 "고객 돈은 빠져나갔는데 주문은 진행할 수 없다"이고, 처리도 같아야
 * 한다. 한쪽만 고치는 일이 생기지 않도록 여기 모아 둔다.
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> PG 호출이 끼어 있어, DB 쓰기는 {@link PaymentRecordService}의
 * 짧은 트랜잭션이 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCompensationService {

    private final PgClient pgClient;
    private final PaymentRecordService paymentRecordService;

    /**
     * 결제를 되돌린다. <b>실패해도 예외를 던지지 않는다</b> — 취소를 재시도 큐에 남기는 것까지가
     * 이 메서드의 책임이고, 호출부는 자기 흐름의 결론(예외를 던질지 말지)을 따로 정한다.
     *
     * @param paymentKey PG 식별자를 인자로 받는다. 중복 결제로 기록이 롤백된 경우 엔티티에는 키가
     *                   남아 있지 않고, PG 응답으로 받은 값만이 취소에 쓸 수 있는 유일한 식별자다
     */
    public void compensate(Long paymentId, String paymentKey, long amount, String reason) {
        var cancel = paymentRecordService.beginCancel(paymentId, reason, amount);
        try {
            PgClient.Cancellation cancellation = pgClient.cancel(paymentKey, amount, reason, cancel.getId());
            paymentRecordService.completeCancel(cancel.getId(), cancellation.pgCancelKey());
        } catch (RuntimeException e) {
            // 여기서 멈추면 고객 돈이 묶인 채로 남는다. 재시도 큐가 이어받는다.
            log.error("보상 취소 실패. 재시도 큐로 넘긴다: paymentId={}, reason={}", paymentId, reason, e);
            paymentRecordService.failCancel(cancel.getId(), e.getMessage());
        }
    }
}
