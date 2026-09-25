package com.kurly.wms.presentation.dto;

import com.kurly.wms.infrastructure.entity.Task;
import com.kurly.wms.infrastructure.entity.Task.TaskStatus;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        String taskNo,
        TaskStatus status,
        Long warehouseId,
        Long outboundOrderId,
        Long outboundItemId,
        Long workerId,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTaskNo(),
                task.getStatus(),
                task.getWarehouse().getId(),
                task.getOutboundOrder().getId(),
                task.getOutboundItem().getId(),
                task.getWorkerId(),
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreatedAt()
        );
    }
}
