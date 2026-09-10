package com.kurly.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

/**
 * 인가 정책이 표기되지 않은 핸들러가 있으면 기동을 중단시킨다.
 *
 * <p>Spring Security의 "기본 거부"와 같은 보장을 얻되, 런타임 거부가 아니라
 * 기동 시점에 누락을 드러낸다. 새 엔드포인트를 추가한 사람은 공개/보호 결정을
 * 반드시 내려야 하며, 내리지 않으면 애플리케이션이 뜨지 않는다.
 *
 * <p>{@link SmartInitializingSingleton}을 쓰는 이유는 이 콜백이 컨텍스트 리프레시 중,
 * <b>웹 서버가 포트를 열기 전에</b> 실행되기 때문이다. {@code ApplicationReadyEvent}를 쓰면
 * 이미 트래픽을 받은 뒤라 잠깐이라도 무방비 상태가 생긴다.
 */
@Slf4j
@RequiredArgsConstructor
public class HandlerAuthorizationAuditor implements SmartInitializingSingleton {

    private final ObjectProvider<RequestMappingHandlerMapping> handlerMappings;
    private final JwtVerificationProperties properties;

    @Override
    public void afterSingletonsInstantiated() {
        List<String> unclassified = handlerMappings.stream()
                .map(RequestMappingHandlerMapping::getHandlerMethods)
                .flatMap(handlers -> handlers.entrySet().stream())
                .filter(entry -> isAudited(entry.getValue()))
                .filter(entry -> HandlerAuthorizationRules.resolve(entry.getValue()).isEmpty())
                .map(this::describe)
                .sorted()
                .toList();

        if (!unclassified.isEmpty()) {
            throw new IllegalStateException("""
                    인가 정책이 지정되지 않은 엔드포인트가 있습니다. \
                    @PublicApi, @Authenticated, @RequireRole 중 하나를 반드시 표기해야 합니다.
                    %s""".formatted(String.join("\n", unclassified)));
        }
        log.info("엔드포인트 인가 정책 검사 통과 (검사 패키지: {})", String.join(", ", properties.auditPackages()));
    }

    /** 프레임워크가 제공하는 핸들러(BasicErrorController, actuator 등)는 검사 대상이 아니다. */
    private boolean isAudited(HandlerMethod handlerMethod) {
        String packageName = handlerMethod.getBeanType().getPackageName();

        if (packageName.startsWith("org.springdoc")) {
            return false;
        }

        for (String audited : properties.auditPackages()) {
            if (packageName.startsWith(audited)) {
                return true;
            }
        }
        return false;
    }

    private String describe(Map.Entry<?, HandlerMethod> entry) {
        HandlerMethod handlerMethod = entry.getValue();
        return "  - %s → %s#%s".formatted(
                entry.getKey(),
                handlerMethod.getBeanType().getSimpleName(),
                handlerMethod.getMethod().getName());
    }
}
