package com.kurly.common.config;

import com.kurly.common.security.AuthPrincipalArgumentResolver;
import com.kurly.common.security.AuthenticationInterceptor;
import com.kurly.common.security.FallbackJwkSource;
import com.kurly.common.security.HandlerAuthorizationAuditor;
import com.kurly.common.security.JwtVerificationProperties;
import com.kurly.common.security.JwtVerifier;
import com.kurly.common.security.NimbusJwtVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
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
    @Bean
    @ConditionalOnMissingBean(JWKSource.class)
    public JWKSource<SecurityContext> jwkSource(JwtVerificationProperties properties) throws Exception {
        if (!StringUtils.hasText(properties.jwksUri())) {
            throw new IllegalStateException(
                    "kurly.security.jwks-uri가 필요합니다. 자체 JWKSource 빈을 등록한 경우는 예외입니다.");
        }
        JWKSource<SecurityContext> remote = JWKSourceBuilder
                .<SecurityContext>create(URI.create(properties.jwksUri()).toURL())
                .retrying(true)          // 일시적 네트워크 오류 재시도
                .rateLimited(true)       // 미지의 kid 폭주로 인한 재조회 증폭 차단
                .refreshAheadCache(true) // 만료 전 미리 갱신
                .outageTolerant(true)    // JWKS 장애 시 만료된 캐시라도 사용
                .build();
        return applyFallback(remote, properties);
    }

    private JWKSource<SecurityContext> applyFallback(JWKSource<SecurityContext> remote,
                                                     JwtVerificationProperties properties) {
        if (!StringUtils.hasText(properties.fallbackJwk())) {
            return remote;
        }
        try {
            return new FallbackJwkSource(remote, JWK.parse(properties.fallbackJwk()));
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
    public WebMvcConfigurer securityWebMvcConfigurer(JwtVerifier jwtVerifier) {
        return new SecurityWebMvcConfigurer(jwtVerifier);
    }

    @RequiredArgsConstructor
    static class SecurityWebMvcConfigurer implements WebMvcConfigurer {

        private final JwtVerifier jwtVerifier;

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            // 경로 패턴으로 공개 여부를 가르지 않는다. 판단은 핸들러 애노테이션이 한다.
            registry.addInterceptor(new AuthenticationInterceptor(jwtVerifier))
                    .addPathPatterns("/**")
                    .excludePathPatterns("/error");
        }

        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new AuthPrincipalArgumentResolver());
        }
    }
}
