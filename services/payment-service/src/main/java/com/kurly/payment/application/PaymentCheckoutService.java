package com.kurly.payment.application;

import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.exception.AmountMismatchException;
import com.kurly.payment.exception.DuplicatePaymentRequestException;
import com.kurly.payment.exception.InvalidPaymentStatusException;
import com.kurly.payment.exception.OrderNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 결제 승인(주문-결제 시퀀스 단계 2·3).
 *
 * <p><b>트랜잭션을 걸지 않는다.</b> PG·주문 서비스 호출이 섞여 있어 트랜잭션으로 감싸면 외부 응답을
 * 기다리는 내내 DB 커넥션을 붙잡는다. DB 쓰기는 {@link PaymentRecordService}의 짧은 트랜잭션이 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCheckoutService {

    private final OrderClient orderClient;
    private final PgClient pgClient;
    private final PaymentRecordService paymentRecordService;
    private final PaymentCompensationService paymentCompensationService;

    /**
     * 결제를 승인한다.
     *
     * <p>금액과 소유자는 <b>클라이언트가 보낸 값을 믿지 않고</b> 주문 서비스에서 받아 대조한다.
     * 요청 금액을 그대로 승인하면 금액 위변조를 막을 수 없다.
     *
     * @return 승인된 결제
     */
    public Payment checkout(Long userId, Long orderId, String paymentKey, long amount) {
        OrderClient.OrderSnapshot order = orderClient.fetch(orderId);
        verifyOrder(order, userId, amount);

        Payment payment = paymentRecordService.createRequested(orderId, userId, amount);

        PgClient.Approval approval;
        try {
            approval = pgClient.approve(paymentKey, orderId, amount);
        } catch (RuntimeException e) {
            // 승인 실패는 결제 행에 남긴다. 흔적이 없으면 고객 문의에 답할 수 없다.
            paymentRecordService.recordFailure(payment.getId());
            throw e;
        }

        Payment approved;
        try {
            approved = paymentRecordService.recordApproval(payment.getId(), approval);
        } catch (DataIntegrityViolationException e) {
            // 같은 주문에 이미 성공한 결제가 있다. DB 유니크 제약이 최종 방어선이며, 여기 걸렸다는
            // 것은 동시 요청이 검증을 함께 통과했다는 뜻이다. 방금 승인된 건은 되돌려야 한다.
            log.error("주문 중복 결제 감지. 방금 승인분을 취소한다: orderId={}, paymentId={}",
                    orderId, payment.getId(), e);
            paymentCompensationService.compensate(
                    payment.getId(), approval.paymentKey(), amount, "DUPLICATE_PAYMENT");
            throw new DuplicatePaymentRequestException();
        }

        notifyOrderOrCompensate(approved, orderId);
        return approved;
    }

    private void verifyOrder(OrderClient.OrderSnapshot order, Long userId, long amount) {
        if (!order.ownerUserId().equals(userId)) {
            // 타인의 주문을 결제하려는 시도다. 주문의 존재 여부를 구분해 노출하지 않는다.
            log.warn("타인 주문 결제 시도: orderId={}", order.orderId());
            throw new OrderNotFoundException();
        }
        if (order.totalAmount() != amount) {
            // 금액 위변조는 별도로 추적·경보해야 할 사건이라 전용 코드로 던진다.
            log.warn("결제 금액 불일치: orderId={}, 주문={}, 요청={}",
                    order.orderId(), order.totalAmount(), amount);
            throw new AmountMismatchException();
        }
        if (!order.payable()) {
            throw new InvalidPaymentStatusException();
        }
    }

    /**
     * 결제 완료를 주문 서비스에 통보한다.
     *
     * <p>주문이 이미 만료됐다면(결제 유효시간 5분 초과) 고객 돈만 빠져나간 상태이므로 즉시
     * 보상 취소를 건다. 보상 취소마저 실패하면 재시도 큐에 남겨 배치가 이어받는다
     * (주문-결제 시퀀스 1절 단계 3).
     */
    private void notifyOrderOrCompensate(Payment payment, Long orderId) {
        try {
            orderClient.completePayment(
                    orderId, payment.getId(), payment.getTotalAmount(), payment.getApprovedAt());
        } catch (OrderClient.OrderAlreadyExpiredException e) {
            log.warn("주문 만료로 보상 취소 수행: paymentId={}, orderId={}", payment.getId(), orderId);
            paymentCompensationService.compensate(
                    payment.getId(), payment.getPaymentKey(), payment.getTotalAmount(), "ORDER_EXPIRED");
            throw new InvalidPaymentStatusException();
        }
    }
}
