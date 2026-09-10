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

    /**
     * 대사가 필요한 결제를 선점하며 가져온다.
     *
     * <p>대상은 셋이다. {@code REQUESTED}(승인 결과를 못 받고 멈춘 건), {@code FAILED}(타임아웃을
     * 실패로 기록했지만 PG는 승인했을 수 있는 건), 그리고 <b>주문에 인계되지 못한 {@code SUCCESS}</b>
     * (승인은 기록됐는데 그 뒤 프로세스가 죽어 주문이 모르는 건)이다.
     *
     * <p>선점은 만료되는 임대로 건다. 완료 표시와 겸하면 워커가 죽었을 때 그 건이 영구히 묻힌다.
     *
     * @param now         임대 만료 판정 기준
     * @param staleBefore 이 시각 이전에 요청된 건만. 진행 중인 정상 결제를 건드리지 않기 위한 유예다
     */
    java.util.List<Payment> claimReconcilableForUpdateSkipLocked(
            java.time.LocalDateTime now, java.time.LocalDateTime staleBefore, int limit);
}
