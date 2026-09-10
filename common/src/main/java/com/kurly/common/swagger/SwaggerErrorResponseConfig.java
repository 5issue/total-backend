package com.kurly.common.swagger;

import com.kurly.common.exception.ErrorCode;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.common.response.ResultStatus;
import com.kurly.common.security.Authenticated;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
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

            // 2. 수동 지정된 커스텀 메시지 어노테이션(@ApiErrorCodeExample, @ApiErrorCodeExamples) 수집
            List<ApiErrorCodeExample> manualExamples = findManualAnnotations(handlerMethod);
            for (ApiErrorCodeExample ex : manualExamples) {
                ErrorCode errorCode = findErrorCodeEnum(ex.status(), ex.code());
                if (errorCode != null) {
                    String msg = ex.message().isBlank() ? errorCode.getMessage() : ex.message();
                    statusGroup.computeIfAbsent(errorCode.getStatus().value(), k -> new ArrayList<>())
                            .add(new TargetError(errorCode, msg));
                }
            }

            // 3. 간소화 어노테이션(@ApiErrors) 수집
            List<ApiErrors> simpleErrorsList = findSimpleAnnotations(handlerMethod);
            for (ApiErrors simpleErrors : simpleErrorsList) {
                for (Class<? extends ErrorCode> clazz : simpleErrors.value()) {
                    ErrorCode[] constants = clazz.getEnumConstants();
                    if (constants != null) {
                        for (ErrorCode ec : constants) {
                            statusGroup.computeIfAbsent(ec.getStatus().value(), k -> new ArrayList<>())
                                    .add(new TargetError(ec, ec.getMessage()));
                        }
                    }
                }
            }

            if (!statusGroup.isEmpty()) {
                injectResponses(operation.getResponses(), statusGroup);
            }

            return operation;
        };
    }

    /**
     * Controller 메서드 및 인터페이스 메서드에서 @ApiErrorCodeExample 과 @ApiErrorCodeExamples 컨테이너를 모두 추출
     */
    private List<ApiErrorCodeExample> findManualAnnotations(HandlerMethod handlerMethod) {
        List<ApiErrorCodeExample> result = new ArrayList<>();

        extractExamples(handlerMethod.getMethod(), result);

        for (Class<?> iface : handlerMethod.getBeanType().getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(handlerMethod.getMethod().getName())) {
                    extractExamples(method, result);
                }
            }
        }
        return result;
    }

    private void extractExamples(Method method, List<ApiErrorCodeExample> targetList) {
        ApiErrorCodeExample single = method.getAnnotation(ApiErrorCodeExample.class);
        if (single != null) {
            targetList.add(single);
        }
        ApiErrorCodeExamples multiple = method.getAnnotation(ApiErrorCodeExamples.class);
        if (multiple != null) {
            targetList.addAll(Arrays.asList(multiple.value()));
        }
    }

    private List<ApiErrors> findSimpleAnnotations(HandlerMethod handlerMethod) {
        List<ApiErrors> result = new ArrayList<>();
        ApiErrors controllerAnno = handlerMethod.getMethodAnnotation(ApiErrors.class);
        if (controllerAnno != null) {
            result.add(controllerAnno);
        }

        for (Class<?> iface : handlerMethod.getBeanType().getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(handlerMethod.getMethod().getName())) {
                    ApiErrors ifaceAnno = method.getAnnotation(ApiErrors.class);
                    if (ifaceAnno != null) {
                        result.add(ifaceAnno);
                    }
                }
            }
        }
        return result;
    }

    private void injectResponses(ApiResponses responses, Map<Integer, List<TargetError>> statusGroup) {
        statusGroup.forEach((statusCode, errorList) -> {
            String statusKey = String.valueOf(statusCode);
            ApiResponse apiResponse = responses.computeIfAbsent(
                    statusKey,
                    k -> new ApiResponse().description("Error Response")
            );

            Content content = apiResponse.getContent();
            if (content == null) {
                content = new Content();
                apiResponse.setContent(content);
            }

            MediaType mediaType = content.getOrDefault("application/json", new MediaType());

            if (mediaType.getSchema() == null) {
                mediaType.setSchema(new Schema<>().type("object"));
            }

            for (TargetError target : errorList) {
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

                String exampleKey = msg.equals(ec.getMessage())
                        ? ec.getCode()
                        : ec.getCode() + " (" + msg + ")";

                mediaType.addExamples(exampleKey, example);
            }

            content.addMediaType("application/json", mediaType);
            apiResponse.setContent(content);
        });
    }

    private ErrorCode findErrorCodeEnum(Class<? extends ErrorCode> enumClass, String codeName) {
        ErrorCode[] constants = enumClass.getEnumConstants();
        if (constants == null) return null;

        for (ErrorCode constant : constants) {
            if (constant.name().equalsIgnoreCase(codeName) || constant.getCode().equalsIgnoreCase(codeName)) {
                return constant;
            }
        }
        return null;
    }

    private record TargetError(ErrorCode errorCode, String customMessage) {
    }
}