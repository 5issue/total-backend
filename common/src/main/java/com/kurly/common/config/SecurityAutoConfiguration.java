package com.kurly.common.config;

import com.kurly.common.security.*;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.net.URI;
import java.text.ParseException;
import java.util.List;

/**
 * 공통 인증 처리기 자동설정.
 *
 * <p><b>opt-in이다.</b> {@code kurly.security.enabled=true}인 서비스에서만 활성화된다.
 * 준비되지 않은 서비스가 common 의존만으로 갑자기 차단되는 일이 없도록 하기 위함이다.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "kurly.security", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(JwtVerificationProperties.class)
public class SecurityAutoConfiguration {

    /**
     * 원격 JWKS 기반 키 공급자.
     * auth-service처럼 서명키를 직접 보유한 서비스는 자체 {@code JWKSource} 빈을 등록하면
     * 이 빈이 만들어지지 않는다.
     */
    /** 평문 JWKS를 허용할 로컬 호스트. */
    private static final List<String> LOOPBACK_HOSTS = List.of("localhost", "127.0.0.1", "[::1]");

    /**
     * 쿠버네티스 클러스터 내부 DNS 접미사. 이 이름은 클러스터 밖에서 해석되지도 라우팅되지도 않는다.
     *
     * <p>운영은 ALB에서 TLS를 종료하고 내부 구간은 재암호화하지 않는 것이 확정 사항이므로
     * (인증인가_설계서 0장·3.2), 서비스 간 JWKS 조회는 평문일 수밖에 없다. 여기서 https를
     * 강제하면 전 서비스가 기동하지 못한다. 차단하려는 대상은 인터넷 구간의 평문 JWKS다.
     *
     * <p>네임스페이스는 여기서 가리지 않는다. common은 모든 서비스가 쓰는 모듈이라 특정
     * 네임스페이스를 알아서는 안 되고, 실제 주소는 각 서비스의 {@code jwks-uri} 설정에 있다.
     */
    private static final String IN_CLUSTER_DNS_SUFFIX = ".svc.cluster.local";

    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    public JWKSource<SecurityContext> jwkSource(JwtVerificationProperties properties) throws Exception {
        if (!StringUtils.hasText(properties.jwksUri())) {
            throw new IllegalStateException(
                    "kurly.security.jwks-uri가 필요합니다. 자체 JWKSource 빈을 등록한 경우는 예외입니다.");
        }
        URI jwksUri = URI.create(properties.jwksUri());
        requireSecureTransport(jwksUri);

        JWKSource<SecurityContext> remote = JWKSourceBuilder
                .<SecurityContext>create(jwksUri.toURL())
                .retrying(true)          // 일시적 네트워크 오류 재시도
                .rateLimited(true)       // 미지의 kid 폭주로 인한 재조회 증폭 차단
                .refreshAheadCache(true) // 만료 전 미리 갱신
                .outageTolerant(true)    // JWKS 장애 시 만료된 캐시라도 사용
                .build();
        return applyFallback(remote, properties);
    }

    /**
     * JWKS는 평문으로 받아서는 안 된다. 중간자가 응답을 바꿔치기하면 자신이 가진 개인키로 서명한
     * 토큰이 검증을 통과해 인증 체계 전체가 무너진다.
     *
     * <p>프로파일이 아니라 호스트로 판별한다. 로컬은 localhost를 쓰므로 별도 분기가 필요 없고,
     * 운영 설정이 실수로 http가 되면 프로파일과 무관하게 막힌다.
     */
    private void requireSecureTransport(URI jwksUri) {
        if ("https".equalsIgnoreCase(jwksUri.getScheme())) {
            return;
        }
        String host = jwksUri.getHost();
        if (host != null && (LOOPBACK_HOSTS.contains(host) || host.endsWith(IN_CLUSTER_DNS_SUFFIX))) {
            return;
        }
        throw new IllegalStateException(
                "kurly.security.jwks-uri는 https여야 합니다. 평문은 로컬 호스트와 클러스터 내부 주소(%s)만 허용합니다: %s"
                        .formatted(IN_CLUSTER_DNS_SUFFIX, jwksUri));
    }

    private JWKSource<SecurityContext> applyFallback(JWKSource<SecurityContext> remote,
                                                     JwtVerificationProperties properties) {
        if (!StringUtils.hasText(properties.fallbackJwk())) {
            return remote;
        }
        try {
            return new FallbackJwkSource(remote, JWK.parse(properties.fallbackJwk()), properties.fallbackWindow());
        } catch (ParseException e) {
            throw new IllegalStateException("kurly.security.fallback-jwk를 JWK로 해석하지 못했습니다.", e);
        }
    }

    @Bean
    @ConditionalOnMissingBean(JwtVerifier.class)
    public JwtVerifier jwtVerifier(JWKSource<SecurityContext> jwkSource, JwtVerificationProperties properties) {
        return new NimbusJwtVerifier(jwkSource, properties);
    }

    @Bean
    public HandlerAuthorizationAuditor handlerAuthorizationAuditor(
            ObjectProvider<RequestMappingHandlerMapping> handlerMappings,
            JwtVerificationProperties properties) {
        return new HandlerAuthorizationAuditor(handlerMappings, properties);
    }

    @Bean
    public WebMvcConfigurer securityWebMvcConfigurer(
            JwtVerifier jwtVerifier,
            @Value("${kurly.cors.allowed-origins:http://localhost:8080,http://127.0.0.1:8080}")
            List<String> allowedOrigins) {
        return new SecurityWebMvcConfigurer(jwtVerifier, allowedOrigins);
    }

    @RequiredArgsConstructor
    static class SecurityWebMvcConfigurer implements WebMvcConfigurer {

        private final JwtVerifier jwtVerifier;
        private final List<String> allowedOrigins;

        @Override
        public void addCorsMappings(CorsRegistry registry) {
            registry.addMapping("/**")
                    .allowedOriginPatterns(allowedOrigins.toArray(String[]::new))
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // 경로 패턴으로 공개 여부를 가르지 않는다. 판단은 핸들러 애노테이션이 한다.
            registry.addInterceptor(new AuthenticationInterceptor(jwtVerifier))
                    .addPathPatterns("/**")
                    .excludePathPatterns(
                            "/error",
                            "/v3/api-docs",
                            "/v3/api-docs/**",
                            "/swagger-ui/**",
                            "/swagger-ui.html"
                    );
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthPrincipalArgumentResolver());
        }
    }
}
