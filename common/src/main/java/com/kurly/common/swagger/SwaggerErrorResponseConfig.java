package com.kurly.common.swagger;

import com.kurly.common.exception.ErrorCode;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ApiResponse;
import com.kurly.common.response.ResultStatus;
import com.kurly.common.security.Authenticated;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;

@AutoConfiguration
public class SwaggerErrorResponseConfig {

    @Bean
    public OperationCustomizer customizeErrorResponses() {
        return (Operation operation, HandlerMethod handlerMethod) -> {
            Map<Integer, List<TargetError>> statusGroup = new LinkedHashMap<>();

            // 1. @Authenticated 선언 시 401 UNAUTHORIZED 자동 주입
            if (handlerMethod.getBeanType().isAnnotationPresent(Authenticated.class)
                    || handlerMethod.hasMethodAnnotation(Authenticated.class)) {
                statusGroup.computeIfAbsent(GlobalErrorCode.UNAUTHORIZED.getStatus().value(), k -> new ArrayList<>())
                        .add(new TargetError(GlobalErrorCode.UNAUTHORIZED, GlobalErrorCode.UNAUTHORIZED.getMessage()));
            }

            // 2. 수동 지정된 커스텀 메시지 어노테이션(@ApiErrorCodeExample) 수집
            Set<ApiErrorCodeExample> manualExamples = findManualAnnotations(handlerMethod);
            for (ApiErrorCodeExample ex : manualExamples) {
                ErrorCode errorCode = findErrorCodeEnum(ex.status(), ex.code());
                if (errorCode != null) {
                    String msg = ex.message().isBlank() ? errorCode.getMessage() : ex.message();
                    statusGroup.computeIfAbsent(errorCode.getStatus().value(), k -> new ArrayList<>())
                            .add(new TargetError(errorCode, msg));
                }
            }

            // 3. 간소화 어노테이션(@ApiErrors) 수집 (클래스 내 Enum 기본 메시지 사용)
            ApiErrors simpleErrors = findAnnotation(handlerMethod, ApiErrors.class);
            if (simpleErrors != null) {
                for (Class<? extends ErrorCode> clazz : simpleErrors.value()) {
                    for (ErrorCode ec : clazz.getEnumConstants()) {
                        statusGroup.computeIfAbsent(ec.getStatus().value(), k -> new ArrayList<>())
                                .add(new TargetError(ec, ec.getMessage()));
                    }
                }
            }

            if (!statusGroup.isEmpty()) {
                injectResponses(operation.getResponses(), statusGroup);
            }

            return operation;
        };
    }

    private Set<ApiErrorCodeExample> findManualAnnotations(HandlerMethod handlerMethod) {
        Set<ApiErrorCodeExample> result = new LinkedHashSet<>();
        result.addAll(AnnotatedElementUtils.findAllMergedAnnotations(handlerMethod.getMethod(), ApiErrorCodeExample.class));

        for (Class<?> iface : handlerMethod.getBeanType().getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(handlerMethod.getMethod().getName())
                        && method.getParameterCount() == handlerMethod.getMethod().getParameterCount()) {
                    result.addAll(AnnotatedElementUtils.findAllMergedAnnotations(method, ApiErrorCodeExample.class));
                }
            }
        }
        return result;
    }

    private <A extends java.lang.annotation.Annotation> A findAnnotation(HandlerMethod handlerMethod, Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), annotationType);
        if (annotation != null) return annotation;

        for (Class<?> iface : handlerMethod.getBeanType().getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(handlerMethod.getMethod().getName())
                        && method.getParameterCount() == handlerMethod.getMethod().getParameterCount()) {
                    annotation = AnnotatedElementUtils.findMergedAnnotation(method, annotationType);
                    if (annotation != null) return annotation;
                }
            }
        }
        return null;
    }

    private void injectResponses(ApiResponses responses, Map<Integer, List<TargetError>> statusGroup) {
        statusGroup.forEach((statusCode, errorList) -> {
            String statusKey = String.valueOf(statusCode);
            io.swagger.v3.oas.models.responses.ApiResponse apiResponse = responses.computeIfAbsent(
                    statusKey,
                    k -> new io.swagger.v3.oas.models.responses.ApiResponse().description("Error Response")
            );

            Content content = apiResponse.getContent() == null ? new Content() : apiResponse.getContent();
            MediaType mediaType = content.getOrDefault("application/json", new MediaType());

            if (mediaType.getSchema() == null) {
                mediaType.setSchema(new Schema<ApiResponse<Void>>().$ref("#/components/schemas/ApiResponse"));
            }

            for (int i = 0; i < errorList.size(); i++) {
                TargetError target = errorList.get(i);
                ErrorCode ec = target.errorCode;
                String msg = target.customMessage;

                Example example = new Example();
                example.setDescription(msg);

                Map<String, Object> errorBody = new LinkedHashMap<>();
                errorBody.put("status", ResultStatus.ERROR.name());
                errorBody.put("message", msg);
                errorBody.put("data", null);
                errorBody.put("error", ec.getCode());
                errorBody.put("timestamp", Instant.now().toString());

                example.setValue(errorBody);
                mediaType.addExamples(ec.getCode() + "_" + (i + 1) + " (" + msg + ")", example);
            }

            content.addMediaType("application/json", mediaType);
            apiResponse.setContent(content);
        });
    }

    private ErrorCode findErrorCodeEnum(Class<? extends ErrorCode> enumClass, String codeName) {
        for (ErrorCode constant : enumClass.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(codeName) || constant.getCode().equalsIgnoreCase(codeName)) {
                return constant;
            }
        }
        return null;
    }

    private record TargetError(ErrorCode errorCode, String customMessage) {
    }
}