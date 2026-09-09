package com.kurly.common.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 원격 JWKS 조회가 실패하거나 일치하는 키를 찾지 못할 때 설정에 담긴 공개키로 대체한다.
 *
 * <p>auth-service가 내려간 상태에서 다른 서비스가 콜드 스타트하면 JWKS 캐시가 비어 있어
 * 모든 토큰 검증이 실패한다. 이 폴백은 그 연쇄 장애를 막는다.
 * 정상 상황에서는 원격 결과가 우선하므로 키 회전 반영이 늦어지지 않는다.
 */
@Slf4j
public class FallbackJwkSource implements JWKSource<SecurityContext> {

    private final JWKSource<SecurityContext> primary;
    private final JWKSet fallback;

    public FallbackJwkSource(JWKSource<SecurityContext> primary, JWK fallbackKey) {
        this.primary = primary;
        this.fallback = new JWKSet(fallbackKey.toPublicJWK());
    }

    /**
     * <b>폴백은 조회 자체가 실패했을 때만 쓴다.</b> 원격이 정상 응답했는데 해당 {@code kid}가 없다는
     * 것은 그 키가 폐기됐다는 뜻이므로, 이때 폴백하면 유출된 개인키로 서명한 토큰이 계속 통과한다.
     * 빈 결과는 빈 결과 그대로 돌려준다.
     */
    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        try {
            return primary.get(selector, context);
        } catch (Exception e) {
            log.warn("원격 JWKS 조회 실패. 폴백 공개키를 사용한다.", e);
            JWKMatcher matcher = selector.getMatcher();
            return fallback.getKeys().stream().filter(matcher::matches).toList();
        }
    }
}
