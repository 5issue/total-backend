package com.kurly.payment.infrastructure.persistence;

import com.kurly.payment.domain.entity.IdempotencyKey;
import com.kurly.payment.domain.repository.IdempotencyKeyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, Long>, IdempotencyKeyRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends IdempotencyKey> S save(S entity);

    @Override
    Optional<IdempotencyKey> findById(Long id);

    @Override
    void deleteById(Long id);
}
