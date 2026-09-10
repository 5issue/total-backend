package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductMediaJpaRepository extends JpaRepository<ProductMedia, Long> {

    List<ProductMedia> findByProductIdOrderBySequenceAsc(Long productId);

    List<ProductMedia> findByProductIdInAndMediaRole(List<Long> productIds, ProductMedia.MediaRole mediaRole);
}
