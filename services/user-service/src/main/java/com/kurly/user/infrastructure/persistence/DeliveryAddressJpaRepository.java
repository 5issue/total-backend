package com.kurly.user.infrastructure.persistence;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryAddressJpaRepository
        extends JpaRepository<DeliveryAddress, Long>, DeliveryAddressRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends DeliveryAddress> S save(S entity);

    /**
     * {@code clearAutomatically}를 켜지 않는다. 영속성 컨텍스트를 비우면 호출부가 들고 있던
     * 대상 배송지가 준영속이 되어 이어지는 상태 변경이 반영되지 않는다.
     */
    @Override
    @Modifying(flushAutomatically = true)
    @Query("update DeliveryAddress a set a.defaultAddress = false"
            + " where a.userId = :userId and a.defaultAddress = true")
    void clearDefaultOf(@Param("userId") Long userId);
}
