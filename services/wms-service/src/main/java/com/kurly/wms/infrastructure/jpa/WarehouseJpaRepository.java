package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Warehouse;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseJpaRepository extends JpaRepository<Warehouse, Long> {

    /**
     * OMS의 권역별 창고 조회(`GET /internal/v1/wms/warehouses`)가 쓰는 조회. region은
     * 선택 조건이다 — null이면 필터링하지 않는다. Hibernate의 HQL `array_contains`가
     * enum 파라미터를 bytea로 잘못 바인딩해(`character varying[] @> bytea[]` 에러) 네이티브
     * 쿼리 + PostgreSQL `= ANY(...)` 연산자로 우회한다. region은 문자열(enum name)로 받는다 —
     * 네이티브 쿼리는 JPQL과 달리 Java enum 타입을 그대로 바인딩하지 못한다.
     */
    @Query(value = """
            SELECT * FROM warehouse w
            WHERE (:region IS NULL OR :region = ANY(w.regions))
              AND w.is_active = :isActive
            ORDER BY w.id ASC
            """, nativeQuery = true)
    List<Warehouse> search(@Param("region") String region, @Param("isActive") boolean isActive);
}
