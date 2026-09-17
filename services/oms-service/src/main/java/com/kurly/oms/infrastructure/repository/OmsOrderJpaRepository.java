package com.kurly.oms.infrastructure.repository;

import com.kurly.oms.domain.order.OmsOrder;
import com.kurly.oms.domain.order.OmsOrderRepository;
import com.kurly.oms.presentation.dto.OmsOrderSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface OmsOrderJpaRepository extends JpaRepository<OmsOrder, Long>, OmsOrderRepository {

    @Query(value = """
            SELECT new com.kurly.oms.presentation.dto.OmsOrderSummary(
                o.id,
                o.orderId,
                o.orderNo,
                CAST(o.status AS string),
                COUNT(s.id),
                r.regionName,
                fc.centerName
            )
            FROM OmsOrder o
            LEFT JOIN Shipment s ON s.omsOrderId = o.id
            LEFT JOIN TamRegion r ON r.id = o.regionId
            LEFT JOIN FulfillmentCenter fc ON fc.id = s.centerId
            WHERE (:orderNo IS NULL OR o.orderNo = :orderNo)
            AND (:status IS NULL OR CAST(o.status AS string) = :status)
            AND (:regionId IS NULL OR o.regionId = :regionId)
            AND (:centerId IS NULL OR s.centerId = :centerId)
            AND (CAST(:startAt AS timestamp) IS NULL OR o.createdAt >= :startAt)
            AND (CAST(:endAt AS timestamp) IS NULL OR o.createdAt <= :endAt)
            GROUP BY o.id, o.orderId, o.orderNo, o.status, r.regionName, fc.centerName
            ORDER BY o.id DESC
            """,
            countQuery = """
                    SELECT COUNT(DISTINCT o.id)
                    FROM OmsOrder o
                    LEFT JOIN Shipment s ON s.omsOrderId = o.id
                    WHERE (:orderNo IS NULL OR o.orderNo = :orderNo)
                    AND (:status IS NULL OR CAST(o.status AS string) = :status)
                    AND (:regionId IS NULL OR o.regionId = :regionId)
                    AND (:centerId IS NULL OR s.centerId = :centerId)
                    AND (CAST(:startAt AS timestamp) IS NULL OR o.createdAt >= :startAt)
                    AND (CAST(:endAt AS timestamp) IS NULL OR o.createdAt <= :endAt)
                    """)
    Page<OmsOrderSummary> searchOrders(
            @Param("orderNo") String orderNo,
            @Param("status") String status,
            @Param("regionId") Long regionId,
            @Param("centerId") Long centerId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            Pageable pageable
    );

    @Query("""
            SELECT DISTINCT o FROM OmsOrder o
            LEFT JOIN FETCH o.items
            WHERE o.id = :omsOrderId
            """)
    Optional<OmsOrder> findByIdWithItems(@Param("omsOrderId") Long omsOrderId);
}
