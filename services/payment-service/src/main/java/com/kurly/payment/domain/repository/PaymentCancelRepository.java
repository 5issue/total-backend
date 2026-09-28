package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentCancel;

import java.util.Optional;

public interface PaymentCancelRepository {

    <S extends PaymentCancel> S save(S paymentCancel);

    Optional<PaymentCancel> findById(Long id);

    /**
     * 같은 사유로 이미 취소를 만들었는지 확인한다.
     *
     * <p>브로커는 최소 1회 배달을 보장하므로 같은 환불 요청이 두 번 올 수 있다. 사유에 이벤트
     * 식별자를 넣어두고 이 조회로 걸러, <b>같은 반품을 두 번 환불하지 않는다.</b>
     */
    boolean existsByCancelReason(String cancelReason);

    /**
     * 해당 결제에서 성공한 취소 금액의 합.
     *
     * <p>부분 환불이 쌓여 결제 총액에 도달했을 때만 결제를 취소 상태로 옮기기 위해 쓴다.
     * 성공한 건만 센다 — 실패·진행 중인 취소를 더하면 환불되지 않은 금액을 환불된 것으로 본다.
     */
    long sumSucceededAmountByPaymentId(Long paymentId);

    /**
     * 결과를 모른 채 남은 취소를 가져온다.
     *
     * <p>{@code REQUESTED}는 PG를 부르기 직전에 커밋된 상태다. 여기서 프로세스가 죽으면 실패 기록도
     * 재시도 큐 적재도 일어나지 않아, 그 취소는 <b>아무도 이어받지 못한다.</b>
     *
     * <p>{@code SKIP LOCKED}로 잠근다. 여러 인스턴스가 같은 취소를 함께 집으면 재시도 행이 중복 생긴다.
     */
    java.util.List<PaymentCancel> findStaleRequestedForUpdateSkipLocked(
            java.time.LocalDateTime staleBefore, int limit);
}
