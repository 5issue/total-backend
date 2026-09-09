package com.kurly.payment.domain.repository;

import com.kurly.payment.domain.entity.Payment;

import java.util.Optional;

public interface PaymentRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 선언이 서로를 재정의하지 못해
     * 모호성 오류가 난다(해소는 각 JpaRepository의 재선언이 맡는다).
     */
    <S extends Payment> S save(S payment);

    Optional<Payment> findById(Long id);

    /**
     * 소유권 검사를 겸한 단건 조회. {@code userId}를 조건에 함께 걸어 타인의 결제는 조회 단계에서
     * 비어 있는 결과가 되도록 한다. 없는 것과 남의 것을 구분해 노출하지 않기 위함이다.
     */
    Optional<Payment> findByIdAndUserId(Long id, Long userId);

    /** 같은 주문에 이미 성공한 결제가 있는지 확인한다. 중복 결제 차단의 마지막 방어선이다. */
    boolean existsByOrderIdAndStatus(Long orderId, com.kurly.payment.domain.enums.PaymentStatus status);
}
