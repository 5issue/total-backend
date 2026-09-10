package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 취소.
 *
 * <p><b>두 경로가 이 서비스를 함께 쓴다.</b>
 * <ul>
 *   <li>동기: 고객이 주문 서비스를 거쳐 취소를 요청한다. JWT가 전파되어 토큰의 {@code sub}가 요청자다.
 *   <li>비동기: 반품 승인 후 OMS가 발행한 이벤트를 소비한다. 행위자는 관리자이며 사용자 토큰이 없다.
 * </ul>
 *
 * <p>그래서 이 서비스는 <b>JWT에 의존하지 않고 소유자 식별자를 파라미터로 받는다.</b> 동기 경로에서는
 * 토큰에서, 비동기 경로에서는 메시지에서 꺼내 넘긴다.
 *
 * <p>주의: 비동기 경로가 넘기는 식별자는 서명이 없어 <b>인증이 아니라 정합성 검증</b>이다.
 * 불일치는 사용자 오류가 아니라 발행자 버그이므로 호출부가 다르게 다뤄야 한다(404가 아니라 DLQ·경보).
 *
 * <p>트랜잭션을 걸지 않는 이유는 {@link PaymentCheckoutService}와 같다 — PG 호출이 섞여 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;
    private final PaymentRecordService paymentRecordService;

    /**
     * 전액 취소.
     *
     * @param ownerUserId 소유자로 주장된 식별자. {@code null}이면 대조를 건너뛴다
     *                    (관리자 승인처럼 소유자 개념이 없는 흐름)
     * @throws PaymentNotFoundException     결제가 없거나 소유자가 다른 경우
     * @throws InvalidPaymentStatusException 취소할 수 없는 상태인 경우
     */
    public PaymentCancel cancel(Long paymentId, Long ownerUserId, String reason) {
        Payment payment = findCancellable(paymentId, ownerUserId);

        PaymentCancel cancel = paymentRecordService.beginCancel(
                paymentId, reason, payment.getTotalAmount());
        try {
            PgClient.Cancellation cancellation = pgClient.cancel(
                    payment.getPaymentKey(), payment.getTotalAmount(), reason, cancel.getId());
            // 갱신된 인스턴스로 바꿔 든다. beginCancel이 돌려준 것은 상태 전이 전의 스냅샷이다.
            return paymentRecordService.completeCancel(cancel.getId(), cancellation.pgCancelKey());
        } catch (RuntimeException e) {
            // 취소 실패를 그대로 던지면 고객 돈이 묶인 채 잊힌다. 이력에 남기고 배치가 이어받는다.
            log.error("PG 취소 실패. 재시도 큐로 넘긴다: paymentId={}", paymentId, e);
            paymentRecordService.failCancel(cancel.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 취소 가능 여부를 확인한다.
     *
     * <p>소유자 대조와 상태 검사를 모두 여기서 한다. 없는 결제와 타인의 결제를 같은 예외로 다뤄
     * ID를 훑어 남의 결제 존재 여부를 알아내지 못하게 한다.
     *
     * <p>{@code @Transactional}을 붙이지 않는다. 같은 빈 안에서 호출하면 프록시를 타지 않아
     * 애노테이션이 아무 일도 하지 않으면서 경계가 있는 것처럼 보이게 만든다.
     * 조회 한 건이라 저장소 메서드의 트랜잭션으로 충분하다.
     */
    private Payment findCancellable(Long paymentId, Long ownerUserId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(PaymentNotFoundException::new);
        if (ownerUserId != null && !payment.isOwnedBy(ownerUserId)) {
            log.warn("소유자가 다른 결제에 취소 시도: paymentId={}", paymentId);
            throw new PaymentNotFoundException();
        }
        if (!payment.isCancellable()) {
            throw new InvalidPaymentStatusException();
        }
        return payment;
    }
}
