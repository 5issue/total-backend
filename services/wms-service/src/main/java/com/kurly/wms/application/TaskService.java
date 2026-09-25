package com.kurly.wms.application;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.wms.domain.exception.WmsErrorCode;
import com.kurly.wms.infrastructure.entity.OutboundItem;
import com.kurly.wms.infrastructure.entity.OutboundItem.OutboundItemStatus;
import com.kurly.wms.infrastructure.entity.OutboundOrder;
import com.kurly.wms.infrastructure.entity.OutboundOrder.OutboundOrderStatus;
import com.kurly.wms.infrastructure.entity.Task;
import com.kurly.wms.infrastructure.entity.Task.TaskStatus;
import com.kurly.wms.infrastructure.jpa.OutboundItemJpaRepository;
import com.kurly.wms.infrastructure.jpa.OutboundOrderJpaRepository;
import com.kurly.wms.infrastructure.jpa.TaskJpaRepository;
import com.kurly.wms.presentation.dto.TaskCompleteRequest;
import com.kurly.wms.presentation.dto.TaskResponse;
import com.kurly.wms.presentation.dto.TaskStartRequest;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
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

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long warehouseId, TaskStatus status) {
        return taskJpaRepository.search(warehouseId, status).stream()
                .map(TaskResponse::from)
                .toList();
    }

    /**
     * 대기 중인(PENDING) 작업을 작업자 1명이 잡아 시작한다. 별도의 "배정" API 없이 시작 시점에
     * workerId를 받아 한 번에 배정+시작 처리한다 — Worker 엔티티가 아직 없어 배정 이력을 따로
     * 관리할 대상도 없다.
     */
    @Transactional
    public TaskResponse start(TaskStartRequest request) {
        Task task = findPendingTask(request.taskId());
        task.start(request.workerId());

        OutboundOrder outboundOrder = task.getOutboundOrder();
        if (outboundOrder.getStatus() == OutboundOrderStatus.ALLOCATED) {
            outboundOrder.startPicking();
        }
        return TaskResponse.from(task);
    }

    /**
     * 진행 중인(IN_PROGRESS) 작업을 완료 처리한다. 이번 버전은 부분 피킹(shortage)을 지원하지
     * 않으며, 항상 주문 수량({@code orderedQuantity}) 전체를 피킹한 것으로 기록한다.
     *
     * <p>이 Task가 속한 전표의 모든 품목이 PICKED가 됐으면 포장 단계로 넘긴다 — 포장 API는 아직
     * 없으니 우선 전표 상태만 앞서 넘겨둔다.
     */
    @Transactional
    public TaskResponse complete(TaskCompleteRequest request) {
        Task task = findInProgressTask(request.taskId());
        if (!Objects.equals(task.getWorkerId(), request.workerId())) {
            throw new BusinessException(WmsErrorCode.TASK_WORKER_MISMATCH,
                    "작업을 시작한 작업자와 일치하지 않습니다. taskId=" + task.getId()
                            + ", assignedWorkerId=" + task.getWorkerId() + ", requestedWorkerId=" + request.workerId());
        }
        task.complete();

        OutboundItem outboundItem = task.getOutboundItem();
        outboundItem.pick(outboundItem.getOrderedQuantity());

        // 같은 전표의 Task들이 동시에 완료되면 서로의 PICKED를 못 보고 둘 다 포장 전환을 놓칠 수 있어,
        // 전표를 잠근 뒤에 전 품목 PICKED 여부를 확인한다.
        Long outboundOrderId = task.getOutboundOrder().getId();
        OutboundOrder outboundOrder = outboundOrderJpaRepository.findWithPessimisticLockById(outboundOrderId)
                .orElseThrow(() -> new EntityNotFoundException("출고 전표를 찾을 수 없습니다. outboundOrderId=" + outboundOrderId));
        boolean allItemsPicked = !outboundItemJpaRepository.existsByOutboundOrderIdAndStatusNot(
                outboundOrder.getId(), OutboundItemStatus.PICKED);
        if (allItemsPicked) {
            outboundOrder.startPacking();
        }
        return TaskResponse.from(task);
    }

    private Task findPendingTask(Long taskId) {
        Task task = taskJpaRepository.findWithPessimisticLockById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("작업 지시를 찾을 수 없습니다. taskId=" + taskId));
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException(WmsErrorCode.INVALID_TASK_STATUS,
                    "대기 중인 작업 지시가 아닙니다. taskId=" + task.getId() + ", status=" + task.getStatus());
        }
        return task;
    }

    private Task findInProgressTask(Long taskId) {
        Task task = taskJpaRepository.findWithPessimisticLockById(taskId)
                .orElseThrow(() -> new EntityNotFoundException("작업 지시를 찾을 수 없습니다. taskId=" + taskId));
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException(WmsErrorCode.INVALID_TASK_STATUS,
                    "진행 중인 작업 지시가 아닙니다. taskId=" + task.getId() + ", status=" + task.getStatus());
        }
        return task;
    }

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
