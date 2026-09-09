package com.kurly.user.domain.repository;

import com.kurly.user.domain.entity.DeliveryAddress;

import java.util.List;
import java.util.Optional;

public interface DeliveryAddressRepository {

    DeliveryAddress save(DeliveryAddress deliveryAddress);

    /** 목록 조회. 기본 배송지를 맨 위에, 그다음은 최근 등록 순으로 둔다. */
    List<DeliveryAddress> findAllByUserIdOrderByDefaultAddressDescIdDesc(Long userId);

    /**
     * 소유권 검사를 겸한 단건 조회. {@code userId}를 조건에 함께 걸어, 타인의 배송지는
     * 조회 단계에서 비어 있는 결과가 되도록 한다(존재 여부를 구분해 노출하지 않는다).
     */
    Optional<DeliveryAddress> findByIdAndUserId(Long id, Long userId);

    /** 기본 배송지. 데이터가 어긋나 둘 이상이어도 처리할 수 있도록 목록으로 받는다. */
    List<DeliveryAddress> findAllByUserIdAndDefaultAddressTrue(Long userId);

    long countByUserId(Long userId);
}
