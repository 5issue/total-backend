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
}
