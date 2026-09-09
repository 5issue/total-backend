package com.kurly.user.infrastructure.persistence;

import com.kurly.user.domain.entity.DeliveryAddress;
import com.kurly.user.domain.repository.DeliveryAddressRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAddressJpaRepository
        extends JpaRepository<DeliveryAddress, Long>, DeliveryAddressRepository {
}
