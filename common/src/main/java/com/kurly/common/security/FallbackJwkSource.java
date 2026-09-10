package com.kurly.common.security;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 원격 JWKS 조회가 실패하거나 일치하는 키를 찾지 못할 때 설정에 담긴 공개키로 대체한다.
 *
 * <p>auth-service가 내려간 상태에서 다른 서비스가 콜드 스타트하면 JWKS 캐시가 비어 있어
 * 모든 토큰 검증이 실패한다. 이 폴백은 그 연쇄 장애를 막는다.
 * 정상 상황에서는 원격 결과가 우선하므로 키 회전 반영이 늦어지지 않는다.
 *
 * <p><b>폴백에는 기한이 있다.</b> 기한이 없으면 폴백은 키 폐기를 무기한 우회하는 길이 된다.
 * 유출된 키를 auth-service가 JWKS에서 내렸는데 마침 소비자 쪽 조회가 실패하는 중이라면,
 * 그 키로 서명된 토큰이 계속 통과한다. 창을 넘기면 폴백을 멈추고 검증을 실패시킨다 —
 * 요청을 거절하는 편이 폐기된 키를 받아주는 것보다 낫다.
 */
@Slf4j
public class FallbackJwkSource implements JWKSource<SecurityContext> {

    private final JWKSource<SecurityContext> primary;
    private final JWKSet fallback;
    private final Duration window;
    private final Clock clock;

    /**
     * 폴백을 처음 쓰기 시작한 시각. 원격이 한 번이라도 성공하면 지워 창을 초기화한다.
     * 여러 요청 스레드가 동시에 읽고 쓰므로 원자적으로 다룬다.
     */
    private final AtomicReference<Instant> fallbackSince = new AtomicReference<>();

    public FallbackJwkSource(JWKSource<SecurityContext> primary, JWK fallbackKey, Duration window) {
        this(primary, fallbackKey, window, Clock.systemUTC());
    }

    FallbackJwkSource(JWKSource<SecurityContext> primary, JWK fallbackKey, Duration window, Clock clock) {
        this.primary = primary;
        this.fallback = new JWKSet(fallbackKey.toPublicJWK());
        this.window = window;
        this.clock = clock;
    }

    /**
     * <b>폴백은 조회 자체가 실패했을 때만 쓴다.</b> 원격이 정상 응답했는데 해당 {@code kid}가 없다는
     * 것은 그 키가 폐기됐다는 뜻이므로, 이때 폴백하면 유출된 개인키로 서명한 토큰이 계속 통과한다.
     * 빈 결과는 빈 결과 그대로 돌려준다.
     */
    @Override
    public List<JWK> get(JWKSelector selector, SecurityContext context) {
        try {
            List<JWK> keys = primary.get(selector, context);
            // 원격이 살아 있다. 다음 장애 때 창을 처음부터 쓰도록 되돌린다.
            fallbackSince.set(null);
            return keys;
        } catch (Exception e) {
            return fallbackOrFail(selector, e);
        }
    }

    private List<JWK> fallbackOrFail(JWKSelector selector, Exception cause) {
        Instant now = clock.instant();
        Instant since = fallbackSince.compareAndExchange(null, now);
        Instant startedAt = since == null ? now : since;

        if (Duration.between(startedAt, now).compareTo(window) > 0) {
            // 여기서 폴백을 계속 내주면 폐기된 키를 무기한 받아주게 된다.
            log.error("원격 JWKS 조회 실패가 {} 넘게 이어져 폴백을 중단한다. 토큰 검증이 실패한다.", window, cause);
            return List.of();
        }

        log.warn("원격 JWKS 조회 실패. 폴백 공개키를 사용한다(허용 창 {}).", window, cause);
        JWKMatcher matcher = selector.getMatcher();
        return fallback.getKeys().stream().filter(matcher::matches).toList();
    }
}
