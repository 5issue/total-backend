package com.kurly.auth.infrastructure.config;

import com.kurly.auth.infrastructure.security.jwt.JwtKeyProvider;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * auth-service는 서명키를 직접 보유하므로 자기 JWKS 엔드포인트를 HTTP로 조회하지 않는다.
 * common의 원격 {@code JWKSource} 대신 로컬 키 공급자를 쓴다.
 */
@Configuration
@ConditionalOnProperty(prefix = "kurly.security", name = "enabled", havingValue = "true")
public class LocalJwkSourceConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(JwtKeyProvider jwtKeyProvider) {
        // 키 회전 시 즉시 반영되도록 매 검증마다 공급자에서 읽는다.
        return (selector, context) -> selector.select(jwtKeyProvider.publicJwkSet());
    }
}
