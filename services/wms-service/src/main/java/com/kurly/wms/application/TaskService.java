package com.kurly.wms.application;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.infrastructure.entity.OutboundItem;
import com.kurly.wms.infrastructure.entity.OutboundOrder;
import com.kurly.wms.infrastructure.entity.Task;
import com.kurly.wms.infrastructure.jpa.OutboundItemJpaRepository;
import com.kurly.wms.infrastructure.jpa.OutboundOrderJpaRepository;
import com.kurly.wms.infrastructure.jpa.TaskJpaRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private static final DateTimeFormatter TASK_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TaskJpaRepository taskJpaRepository;
    private final OutboundOrderJpaRepository outboundOrderJpaRepository;
    private final OutboundItemJpaRepository outboundItemJpaRepository;

    /**
     * OutboundOrder가 ALLOCATED 될 때({@code OutboundAllocatedEvent}) 호출돼, 포함된 품목마다
     * 피킹 Task를 하나씩 생성한다 — 한 주문이 FEFO로 여러 LOT/로케이션에 분할 할당됐으면 그만큼
     * 여러 건이 생긴다. 이미 Task가 있는 품목(uk_task_outbound_item_id로 DB가 최종 방어)은
     * 건너뛴다 — 이벤트가 중복 처리돼도 안전하다.
     *
     * <p>{@code REQUIRES_NEW}가 필요하다 — {@code @TransactionalEventListener(AFTER_COMMIT)}가
     * 원래 트랜잭션의 커밋 콜백 안에서 이 메서드를 호출하는데, 그 시점엔 참여할 실제 물리
     * 트랜잭션이 없다({@code OutboundOrderService.retryUnallocated()}와 동일한 이유).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createPickingTasks(Long outboundOrderId) {
        OutboundOrder outboundOrder = outboundOrderJpaRepository.findById(outboundOrderId)
                .orElseThrow(() -> new EntityNotFoundException("출고 전표를 찾을 수 없습니다. outboundOrderId=" + outboundOrderId));

        List<Task> tasks = outboundItemJpaRepository.findByOutboundOrderId(outboundOrderId).stream()
                .filter(item -> !taskJpaRepository.existsByOutboundItemId(item.getId()))
                .map(item -> Task.builder()
                        .taskNo(generateTaskNo())
                        .warehouse(outboundOrder.getWarehouse())
                        .outboundOrder(outboundOrder)
                        .outboundItem(item)
                        .build())
                .toList();

        if (tasks.isEmpty()) {
            return;
        }
        taskJpaRepository.saveAll(tasks);
        log.info("피킹 작업 지시 생성 완료. outboundOrderId={}, 생성 건수={}", outboundOrderId, tasks.size());
    }

    /**
     * {@code TSK-PICK-<yyyyMMdd>-<시퀀스>} 형태로 조합한다. 시퀀스는 일자별로 1부터 리셋되는
     * 카운터가 아니라 전역으로 계속 증가하는 값이다 — 리셋 카운터를 만들려면 별도 테이블/락이
     * 더 필요해서, 우선 사람이 식별하기 쉬운 정도의 단순한 채번으로 시작한다.
     */
    private String generateTaskNo() {
        long sequence = taskJpaRepository.nextTaskNoSequence();
        return "TSK-PICK-" + LocalDate.now().format(TASK_NO_DATE_FORMAT) + "-" + sequence;
    }
}
