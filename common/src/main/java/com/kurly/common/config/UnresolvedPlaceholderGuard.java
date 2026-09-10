package com.kurly.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Map;
import java.util.Set;

/**
 * 주입되지 않은 환경변수를 기동 시점에 잡아낸다.
 *
 * <p><b>왜 필요한가.</b> {@code ${DB_HOST}}처럼 기본값 없이 적어두면 환경변수 누락 시 기동이
 * 실패할 것 같지만, Boot의 {@code Binder}는 해석하지 못한 플레이스홀더를 예외로 만들지 않고
 * <b>리터럴 문자열을 그대로 바인딩한다.</b> 그래서 다음이 실제로 일어난다.
 *
 * <ul>
 *   <li>{@code spring.datasource.url} — {@code ${DB_HOST}:${DB_PORT}} 리터럴이 JDBC 드라이버까지
 *       도달해, 원인을 알기 어려운 {@code NumberFormatException}으로 뒤늦게 죽는다</li>
 *   <li>{@code spring.rabbitmq.host} — <b>기동은 성공한다.</b> 연결은 백그라운드에서 재시도되므로
 *       헬스체크는 초록불인데 이벤트는 하나도 나가지 않는다. 가장 위험한 경우다</li>
 *   <li>서비스 간 base-url, OAuth 리다이렉트 화이트리스트 — 리터럴이 정상값으로 받아들여져
 *       첫 호출·첫 로그인에서야 드러난다</li>
 * </ul>
 *
 * <p>설정 클래스마다 같은 검사를 복붙하는 방식은 새 설정이 생길 때마다 빠뜨린다. 여기서 한 번에
 * 훑고, <b>발견된 것을 모두 모아</b> 한 번에 보고한다. 하나씩 고쳐가며 재기동하지 않아도 된다.
 *
 * <p>{@link EnvironmentPostProcessor}로 만든 이유는 <b>컨텍스트가 만들어지기 전에</b> 멈추기
 * 위해서다. 포트가 열리기도 전, DB 커넥션을 시도하기도 전에 중단된다.
 *
 * <p>local·test 프로파일에서는 검사하지 않는다. 로컬 설정은 모든 값에 기본값을 두고 있어 애초에
 * 미해석 플레이스홀더가 남지 않으며, 개발 중 기동을 막을 이유도 없다.
 */
public class UnresolvedPlaceholderGuard implements EnvironmentPostProcessor, Ordered {

    /** 해석되지 않은 플레이스홀더의 흔적. */
    private static final String UNRESOLVED_PREFIX = "${";

    private static final Set<String> EXEMPT_PROFILES = Set.of("local", "test");

    /**
     * 우리 설정 파일에서 온 프로퍼티만 검사한다.
     *
     * <p>환경 전체를 훑으면 Boot 기본값까지 걸린다. {@code logging.pattern.console}처럼
     * {@code ${...}}를 정상적으로 포함하는 값이 있어 오탐이 난다. Boot는 설정 파일에서 온
     * 프로퍼티 소스 이름을 {@code Config resource '...'} 형태로 짓는다.
     */
    private static final String CONFIG_RESOURCE_MARKER = "Config resource";

    @Override
    public int getOrder() {
        // 설정 파일 적재와 프로파일 결정이 끝난 뒤에 돌아야 한다.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isExempt(environment)) {
            return;
        }

        Map<String, String> unresolved = findUnresolved(environment);
        if (unresolved.isEmpty()) {
            return;
        }

        StringBuilder message = new StringBuilder("운영 설정에 주입되지 않은 환경변수가 있습니다:");
        unresolved.forEach((key, value) -> message.append(String.format("%n  %s = %s", key, value)));
        message.append(String.format("%n각 값의 환경변수를 주입하거나, 해당 설정을 제거하세요."));
        throw new IllegalStateException(message.toString());
    }

    private boolean isExempt(ConfigurableEnvironment environment) {
        String[] active = environment.getActiveProfiles();
        // 프로파일을 지정하지 않으면 default(local)로 동작한다. 그때도 검사하지 않는다.
        if (active.length == 0) {
            return true;
        }
        for (String profile : active) {
            if (EXEMPT_PROFILES.contains(profile)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> findUnresolved(ConfigurableEnvironment environment) {
        Map<String, String> unresolved = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)
                    || !source.getName().contains(CONFIG_RESOURCE_MARKER)) {
                continue;
            }
            for (String key : enumerable.getPropertyNames()) {
                unresolvedValueOf(environment, enumerable, key)
                        // 같은 키가 여러 파일에 있으면 우선순위가 높은 쪽만 남긴다.
                        .ifPresent(value -> unresolved.putIfAbsent(key, value));
            }
        }
        return unresolved;
    }

    /**
     * 해석되지 않은 값이면 원문을 돌려준다.
     *
     * <p>{@code Environment.getProperty}는 해석하지 못한 플레이스홀더에 <b>예외를 던진다.</b>
     * 관대하게 리터럴을 남기는 것은 {@code Binder}(= {@code @ConfigurationProperties} 바인딩)이고,
     * 그래서 설정값이 조용히 잘못 주입된다. 여기서는 그 예외가 곧 우리가 찾는 신호이므로,
     * 삼키지 않고 "미해석"으로 받아 원문을 모은다.
     *
     * <p>예외를 잡되 <b>즉시 던지지는 않는다.</b> 발견한 것을 모두 모아 한 번에 보고해야
     * 운영자가 하나씩 고쳐가며 재기동하지 않는다.
     */
    private Optional<String> unresolvedValueOf(ConfigurableEnvironment environment,
                                               EnumerablePropertySource<?> source, String key) {
        try {
            String resolved = environment.getProperty(key);
            // 해석에 성공해도 ${ 가 남아 있으면(중첩 플레이스홀더 등) 같은 문제다.
            return resolved != null && resolved.contains(UNRESOLVED_PREFIX)
                    ? Optional.of(resolved)
                    : Optional.empty();
        } catch (RuntimeException e) {
            Object raw = source.getProperty(key);
            return Optional.of(raw == null ? "(해석 실패)" : String.valueOf(raw));
        }
    }
}
