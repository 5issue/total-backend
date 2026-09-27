package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductConsumedEventJpaRepository extends JpaRepository<ProductConsumedEvent, Long> {

    boolean existsByEventId(String eventId);
}
