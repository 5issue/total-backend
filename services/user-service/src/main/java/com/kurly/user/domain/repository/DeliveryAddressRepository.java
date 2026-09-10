package com.kurly.user.domain.repository;

import com.kurly.user.domain.entity.DeliveryAddress;

import java.util.List;
import java.util.Optional;

public interface DeliveryAddressRepository {

    /**
     * Spring Data의 {@code <S extends T> S save(S)}와 같은 형태로 선언한다.
     * 단순히 {@code T save(T)}로 두면 JpaRepository와 함께 상속했을 때 두 선언이 서로를 재정의하지
     * 못해, 이 타입이 아닌 JpaRepository 타입으로 호출하는 순간 모호성 오류가 난다.
     */
    <S extends DeliveryAddress> S save(S deliveryAddress);

    /** 목록 조회. 기본 배송지를 맨 위에, 그다음은 최근 등록 순으로 둔다. */
    List<DeliveryAddress> findAllByUserIdOrderByDefaultAddressDescIdDesc(Long userId);

    /**
     * 소유권 검사를 겸한 단건 조회. {@code userId}를 조건에 함께 걸어, 타인의 배송지는
     * 조회 단계에서 비어 있는 결과가 되도록 한다(존재 여부를 구분해 노출하지 않는다).
     */
    Optional<DeliveryAddress> findByIdAndUserId(Long id, Long userId);

    /** 기본 배송지. 데이터가 어긋나 둘 이상이어도 처리할 수 있도록 목록으로 받는다. */
    List<DeliveryAddress> findAllByUserIdAndDefaultAddressTrue(Long userId);

    /**
     * 기존 기본 배송지를 해제한다.
     *
     * <p>더티 체킹이 아니라 벌크 갱신으로 <b>즉시</b> 실행한다. 더티 체킹에 맡기면 해제와 신규
     * 지정이 같은 flush에 묶이고, 그 순서를 보장할 수 없어 순간적으로 기본 배송지가 둘이 되어
     * 유니크 제약에 걸린다.
     */
    void clearDefaultOf(Long userId);

    long countByUserId(Long userId);
}
