package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.IdempotencyKey;

import java.util.Optional;

public interface IdempotencyKeyRepository {

    <S extends IdempotencyKey> S save(S idempotencyKey);

    /**
     * 사용자 단위로 키를 찾는다. 키만으로 찾으면 다른 사용자가 같은 UUID를 보냈을 때
     * 남의 결제 응답을 재생하게 된다.
     */
    Optional<IdempotencyKey> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
