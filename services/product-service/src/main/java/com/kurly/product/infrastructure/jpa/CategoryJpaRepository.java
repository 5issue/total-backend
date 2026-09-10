package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryJpaRepository extends JpaRepository<Category, Long> {
    @Query(value = """
            WITH RECURSIVE CategoryHierarchy AS (
                SELECT id FROM category WHERE id = :categoryId
                UNION ALL
                SELECT c.id FROM category c
                INNER JOIN CategoryHierarchy ch ON c.parent_id = ch.id
            )
            SELECT id FROM CategoryHierarchy
            """, nativeQuery = true)
    List<Long> findAllSubCategoryIds(@Param("categoryId") Long categoryId);
}
