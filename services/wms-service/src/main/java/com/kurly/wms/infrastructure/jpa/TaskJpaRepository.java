package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Task;
import com.kurly.wms.infrastructure.entity.Task.TaskStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskJpaRepository extends JpaRepository<Task, Long> {

    boolean existsByOutboundItemId(Long outboundItemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Task> findWithPessimisticLockById(Long id);

    /** warehouseId/status는 전부 선택 조건이다 — null이면 그 조건은 건너뛴다. */
    @Query("""
            SELECT t FROM Task t
            WHERE (:warehouseId IS NULL OR t.warehouse.id = :warehouseId)
              AND (:status IS NULL OR t.status = :status)
            ORDER BY t.createdAt ASC
            """)
    List<Task> search(@Param("warehouseId") Long warehouseId, @Param("status") TaskStatus status);

    /** task_no 채번용 전역 시퀀스의 다음 값을 가져온다. */
    @Query(value = "SELECT nextval('task_no_seq')", nativeQuery = true)
    long nextTaskNoSequence();
}
