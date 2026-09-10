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
