package com.kurly.payment.application;

import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.domain.entity.Payment;
import com.kurly.payment.domain.entity.PaymentCancel;
import com.kurly.payment.domain.entity.PaymentOutbox;
import com.kurly.payment.domain.entity.PaymentRetry;
import com.kurly.payment.domain.repository.PaymentCancelRepository;
import com.kurly.payment.domain.repository.PaymentOutboxRepository;
import com.kurly.payment.domain.repository.PaymentRepository;
import com.kurly.payment.domain.repository.PaymentRetryRepository;
import com.kurly.payment.exception.PaymentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 결제 상태 기록 전용 서비스.
 *
 * <p><b>왜 별도 빈인가</b>: PG 호출은 수 초가 걸릴 수 있다. 그 호출을 트랜잭션 안에서 하면 응답을
 * 기다리는 내내 DB 커넥션을 붙잡는다. 커넥션 풀은 서비스 전체 공용이라, PG가 느려지면 결제와
 * 무관한 엔드포인트까지 함께 마비된다.
 *
 * <p>그래서 트랜잭션은 이 빈의 짧은 메서드들이 담당하고, 외부 호출은 트랜잭션 밖에서 한다.
 * 같은 빈 안에서 호출하면 프록시를 타지 않아 트랜잭션이 걸리지 않으므로 빈을 분리했다.
 */
@Service
@RequiredArgsConstructor
public class PaymentRecordService {

    /** 재시도 상한. 넘으면 중단하고 사람이 본다. */
    static final int MAX_RETRY_COUNT = 5;

    static final String PG_CANCEL_TASK = "PG_CANCEL";
    static final String PAYMENT_CANCELED_EVENT = "PAYMENT_CANCELED";

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentRetryRepository paymentRetryRepository;
    private final JsonMapper jsonMapper;

    /** PG 승인 전에 결제 행을 먼저 남긴다. 승인 결과를 어디에 기록할지 미리 정해두기 위함이다. */
    @Transactional
    public Payment createRequested(Long orderId, Long userId, long amount) {
        return paymentRepository.save(Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .totalAmount(amount)
                .build());
    }

    @Transactional
    public Payment recordApproval(Long paymentId, PgClient.Approval approval) {
        Payment payment = findPayment(paymentId);
        payment.approve(approval.paymentKey(), approval.method(), approval.receiptUrl());
        return payment;
    }

    @Transactional
    public void recordFailure(Long paymentId) {
        findPayment(paymentId).fail();
    }

    /** 취소 시도를 먼저 남긴다. PG 호출이 실패해도 시도한 사실이 남아야 재시도할 수 있다. */
    @Transactional
    public PaymentCancel beginCancel(Long paymentId, String reason, long amount) {
        return paymentCancelRepository.save(PaymentCancel.builder()
                .payment(findPayment(paymentId))
                .cancelReason(reason)
                .cancelAmount(amount)
                .build());
    }

    /**
     * 취소 성공을 기록하고 결제 상태를 옮긴다. 이벤트는 같은 트랜잭션의 아웃박스에 적재한다.
     *
     * <p>브로커 발행을 여기서 직접 하면 DB는 커밋됐는데 발행이 실패하거나 그 반대가 되어
     * 결제 상태와 후속 처리가 어긋난다. 발행은 별도 워커가 아웃박스를 보고 수행한다.
     */
    @Transactional
    public PaymentCancel completeCancel(Long cancelId, String pgCancelKey) {
        return applyCancelSuccess(cancelId, pgCancelKey);
    }

    /**
     * 재시도로 성공한 취소를 확정한다. 취소 반영과 재시도 완료를 한 트랜잭션에서 끝낸다.
     *
     * <p>{@link #completeCancel}을 직접 부르지 않고 공용 메서드를 쓴다. 같은 빈 안에서 호출하면
     * 프록시를 타지 않아, 트랜잭션 경계가 있는 것처럼 보이지만 실제로는 없는 코드가 된다.
     */
    @Transactional
    public void completeRetry(Long retryId, Long cancelId, String pgCancelKey) {
        applyCancelSuccess(cancelId, pgCancelKey);
        paymentRetryRepository.findById(retryId)
                .orElseThrow(PaymentNotFoundException::new)
                .succeed();
    }

    /**
     * 재시도 실패를 기록한다. 상한을 넘기면 중단되고 사람이 봐야 한다.
     *
     * <p>{@link #failCancel}과 달리 재시도 행을 <b>새로 만들지 않는다.</b> 만들면 시도할 때마다
     * 큐가 불어나 같은 작업이 기하급수로 늘어난다.
     */
    @Transactional
    public void failRetry(Long retryId, String error) {
        paymentRetryRepository.findById(retryId)
                .orElseThrow(PaymentNotFoundException::new)
                .recordFailure(error, MAX_RETRY_COUNT);
    }

    /**
     * 실행할 재시도 작업을 선점한다.
     *
     * <p>PG 호출에 필요한 값을 트랜잭션 안에서 미리 뽑아 돌려준다. 엔티티를 그대로 넘기면
     * 트랜잭션이 끝난 뒤 지연 로딩된 {@code payment}에 접근하다 실패한다.
     */
    @Transactional
    public List<RetryTask> claimDueRetries(int limit, Duration lease) {
        return paymentRetryRepository
                .findDueForUpdateSkipLocked(LocalDateTime.now(), limit).stream()
                .peek(retry -> retry.lease(lease))
                .map(retry -> new RetryTask(
                        retry.getId(),
                        retry.getTaskType(),
                        retry.getPayment().getPaymentKey(),
                        readPaymentCancelId(retry.getPayload()),
                        readCancelAmount(retry.getPayload())))
                .toList();
    }

    private Long readPaymentCancelId(String payload) {
        return jsonMapper.readTree(payload).get("paymentCancelId").asLong();
    }

    private long readCancelAmount(String payload) {
        return jsonMapper.readTree(payload).get("cancelAmount").asLong();
    }

    private PaymentCancel applyCancelSuccess(Long cancelId, String pgCancelKey) {
        PaymentCancel cancel = paymentCancelRepository.findById(cancelId)
                .orElseThrow(PaymentNotFoundException::new);
        cancel.succeed(pgCancelKey);

        // 부분 취소를 제공하지 않으므로 취소 성공은 곧 전액 취소다.
        Payment payment = cancel.getPayment();
        payment.cancel();

        paymentOutboxRepository.save(PaymentOutbox.builder()
                .eventType(PAYMENT_CANCELED_EVENT)
                // JWT 원문은 싣지 않는다. 큐는 영속화되고 재시도로 오래 남는다(설계서 3.4).
                .payload(jsonMapper.writeValueAsString(Map.of(
                        "paymentId", payment.getId(),
                        "orderId", payment.getOrderId(),
                        "userId", payment.getUserId(),
                        "canceledAmount", cancel.getCancelAmount())))
                .build());

        // 갱신된 인스턴스를 돌려준다. beginCancel이 돌려준 객체는 다른 트랜잭션에서 읽은 것이라
        // 상태 전이가 반영되어 있지 않아, 그대로 응답에 쓰면 취소했는데 SUCCESS로 나간다.
        return cancel;
    }

    /**
     * 취소 실패를 기록하고 재시도 큐에 넣는다.
     *
     * <p>여기서 멈추면 고객 돈이 묶인 채로 남는다. 반드시 재시도가 보장되어야 한다
     * (주문-결제 시퀀스 1절 단계 3).
     */
    @Transactional
    public void failCancel(Long cancelId, String error) {
        PaymentCancel cancel = paymentCancelRepository.findById(cancelId)
                .orElseThrow(PaymentNotFoundException::new);
        cancel.fail(error);

        paymentRetryRepository.save(PaymentRetry.builder()
                .payment(cancel.getPayment())
                .taskType(PG_CANCEL_TASK)
                .payload(jsonMapper.writeValueAsString(Map.of(
                        "paymentCancelId", cancel.getId(),
                        "cancelAmount", cancel.getCancelAmount())))
                .build());
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId).orElseThrow(PaymentNotFoundException::new);
    }

    /**
     * 배치가 실행할 재시도 작업. 트랜잭션 밖에서 쓰이므로 엔티티가 아닌 값으로 넘긴다.
     *
     * @param paymentKey PG 취소 호출에 필요한 결제 식별자
     */
    public record RetryTask(Long retryId, String taskType, String paymentKey,
                            Long paymentCancelId, long cancelAmount) {
    }
}
