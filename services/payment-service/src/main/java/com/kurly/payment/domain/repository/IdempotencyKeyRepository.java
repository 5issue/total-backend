package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.IdempotencyKey;

import java.util.Optional;

public interface IdempotencyKeyRepository {

    <S extends IdempotencyKey> S save(S idempotencyKey);

    Optional<IdempotencyKey> findById(Long id);

    /**
     * 사용자 단위로 키를 찾는다. 키만으로 찾으면 다른 사용자가 같은 UUID를 보냈을 때
     * 남의 결제 응답을 재생하게 된다.
     */
    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /**
     * 선점을 해제한다. 업무 규칙 위반으로 실패해 <b>결제가 일어나지 않은 것이 확실할 때만</b> 쓴다.
     * 결제 여부를 모르는 상태에서 해제하면 재시도가 이중 결제가 된다.
     */
    void deleteById(Long id);

    /**
     * 오래도록 {@code IN_PROGRESS}로 남은 선점을 지운다.
     *
     * <p>선점만 남고 처리 결과가 없는 키는 같은 키로 오는 재요청을 영원히 409로 막는다. 결제 자체의
     * 진실은 대사가 이미 맞춰 놓았으므로, 여기서 푸는 것은 <b>잠금뿐</b>이다. 재요청은 결제 상태에
     * 따라 정상 처리되거나 중복으로 거절된다.
     *
     * <p><b>아직 실행 중인 요청의 키를 풀면 어떻게 되는가.</b> 유예({@code staleAfter})가 요청의 최대
     * 수명보다 짧으면 이론적으로 그럴 수 있다. 그래도 이중 승인은 일어나지 않는다. 방어선이 셋이다.
     * <ol>
     *   <li>PG 승인 멱등키를 {@code paymentKey}로 고정했다 — 재실행이 같은 키를 보내므로 PG가
     *       원래 응답을 재생한다(TossPgClient)</li>
     *   <li>토스는 이미 승인된 {@code paymentKey}의 재승인을 거절한다</li>
     *   <li>같은 주문의 두 번째 성공은 {@code success_order_id} 유니크 제약이 막고,
     *       걸린 승인분은 보상 취소된다</li>
     * </ol>
     * 유예를 줄일 때는 이 셋이 여전히 성립하는지 확인해야 한다.
     *
     * @return 지운 건수
     */
    int deleteStaleInProgress(java.time.LocalDateTime staleBefore, int limit);

    /**
     * 보관 기간이 지난 {@code COMPLETED} 키를 지운다.
     *
     * <p>{@code IN_PROGRESS}는 건드리지 않는다. 그쪽은 선점이 매달린 것이라
     * {@link #deleteStaleInProgress}가 다른 기준으로 다룬다.
     *
     * @return 지운 건수
     */
    int deleteCompletedBefore(java.time.LocalDateTime before, int limit);
}
