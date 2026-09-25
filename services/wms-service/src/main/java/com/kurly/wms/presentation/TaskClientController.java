package com.kurly.wms.presentation;

import com.kurly.common.response.ApiResponse;
import com.kurly.wms.application.TaskService;
import com.kurly.wms.infrastructure.entity.Task.TaskStatus;
import com.kurly.wms.presentation.dto.TaskCompleteRequest;
import com.kurly.wms.presentation.dto.TaskResponse;
import com.kurly.wms.presentation.dto.TaskStartRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PDA 현장 작업자용 피킹 작업(Task) API. 인증된 사용자만 호출할 수 있다(특정 역할 제한은 없음). */
@Tag(name = "Task")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wms/tasks")
public class TaskClientController {

    private final TaskService taskService;

    @GetMapping
    public ApiResponse<List<TaskResponse>> listTasks(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) TaskStatus status) {
        return ApiResponse.success(taskService.list(warehouseId, status));
    }

    @PostMapping("/start")
    public ApiResponse<TaskResponse> startTask(@Valid @RequestBody TaskStartRequest request) {
        return ApiResponse.success(taskService.start(request));
    }

    @PostMapping("/complete")
    public ApiResponse<TaskResponse> completeTask(@Valid @RequestBody TaskCompleteRequest request) {
        return ApiResponse.success(taskService.complete(request));
    }
}
