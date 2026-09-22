package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.returnorder.OmsReturn;
import com.kurly.oms.domain.returnorder.OmsReturnRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OmsReturnJpaRepository extends JpaRepository<OmsReturn, Long>, OmsReturnRepository {

    boolean existsByOmsOrderId(Long omsOrderId);

    boolean existsBySourceEventId(String sourceEventId);

    @Query("""
            SELECT DISTINCT r FROM OmsReturn r
            LEFT JOIN FETCH r.items ri
            LEFT JOIN FETCH ri.orderItem
            WHERE r.omsOrderId = :omsOrderId
            """)
    Optional<OmsReturn> findByOmsOrderIdWithDetails(@Param("omsOrderId") Long omsOrderId);

    @Query("""
            SELECT DISTINCT r FROM OmsReturn r
            LEFT JOIN FETCH r.items ri
            LEFT JOIN FETCH ri.orderItem
            WHERE r.id = :returnId
            """)
    Optional<OmsReturn> findByIdWithDetails(@Param("returnId") Long returnId);
}
