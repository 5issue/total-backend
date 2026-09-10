package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.PaymentCancel;

import java.util.Optional;

public interface PaymentCancelRepository {

    <S extends PaymentCancel> S save(S paymentCancel);

    Optional<PaymentCancel> findById(Long id);

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
