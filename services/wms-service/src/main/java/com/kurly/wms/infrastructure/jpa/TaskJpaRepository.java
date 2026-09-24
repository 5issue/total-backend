package com.kurly.wms.infrastructure.jpa;

import com.kurly.wms.infrastructure.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskJpaRepository extends JpaRepository<Task, Long> {

    boolean existsByOutboundItemId(Long outboundItemId);

    /** task_no 채번용 전역 시퀀스의 다음 값을 가져온다. */
    @Query(value = "SELECT nextval('task_no_seq')", nativeQuery = true)
    long nextTaskNoSequence();
}
