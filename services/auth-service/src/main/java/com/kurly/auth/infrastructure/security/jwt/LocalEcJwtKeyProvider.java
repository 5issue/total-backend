package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.UUID;

/**
 * EC P-256 서명키 공급자.
 * 운영에서는 KMS 기반 구현으로 교체한다(인증인가_설계서 1.3.2 — 개인키 외부 반출 금지).
 *
 * <p>운영에서도 이 빈이 등록되지만, 그때는 주입된 {@code jwt.private-jwk}를 쓸 뿐
 * <b>키를 생성하지 않는다.</b> 임시 키 생성은 local 프로파일에서만 허용하며,
 * 그 외 환경에서 키가 없으면 기동을 중단한다.
 */
@Slf4j
@Component
public class LocalEcJwtKeyProvider implements JwtKeyProvider {

    /** 해석되지 않은 플레이스홀더의 흔적. Boot의 Binder는 이를 예외로 만들지 않고 리터럴로 남긴다. */
    private static final String UNRESOLVED_PLACEHOLDER_PREFIX = "${";

    private final ECKey ecKey;
    private final JWSSigner signer;
    private final boolean localProfile;

    public LocalEcJwtKeyProvider(JwtProperties properties, Environment environment) {
        this.localProfile = environment.matchesProfiles("local", "test");
        this.ecKey = resolveKey(properties.privateJwk());
        try {
            this.signer = new ECDSASigner(this.ecKey);
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT 서명자를 초기화하지 못했습니다.", e);
        }
    }

    @Override
    public String activeKeyId() {
        return ecKey.getKeyID();
    }

    @Override
    public JWSSigner signer() {
        return signer;
    }

    @Override
    public JWKSet publicJwkSet() {
        return new JWKSet(ecKey.toPublicJWK());
    }

    private ECKey resolveKey(String privateJwk) {
        // 환경변수가 주입되지 않으면 Boot의 Binder가 "${JWT_PRIVATE_JWK}" 리터럴을 그대로 넘긴다.
        // hasText()는 이를 값이 있는 것으로 보므로 별도로 걸러야 한다.
        if (StringUtils.hasText(privateJwk) && privateJwk.startsWith(UNRESOLVED_PLACEHOLDER_PREFIX)) {
            throw new IllegalStateException(
                    "jwt.private-jwk의 환경변수가 주입되지 않았습니다: " + privateJwk);
        }
        if (StringUtils.hasText(privateJwk)) {
            try {
                return ECKey.parse(privateJwk);
            } catch (ParseException e) {
                throw new IllegalStateException("jwt.private-jwk 값을 EC JWK로 해석하지 못했습니다.", e);
            }
        }
        if (!localProfile) {
            // 인스턴스마다 다른 키로 서명하게 되어 JWKS를 받아간 서비스가 검증에 실패하고,
            // 재기동하면 발급한 토큰이 모두 무효가 된다. 운영에서 조용히 넘어가면 안 되는 상태다.
            throw new IllegalStateException(
                    "jwt.private-jwk가 필요합니다. 임시 서명키 생성은 local 프로파일에서만 허용합니다.");
        }
        log.warn("jwt.private-jwk가 비어 있어 임시 서명키를 생성합니다. 재기동하면 기존 토큰은 모두 무효가 됩니다. 로컬 전용 동작입니다.");
        return generate();
    }

    private ECKey generate() {
        try {
            return new ECKeyGenerator(Curve.P_256)
                    .keyID(UUID.randomUUID().toString())
                    .algorithm(JWSAlgorithm.ES256)
                    .generate();
        } catch (JOSEException e) {
            throw new IllegalStateException("EC 서명키를 생성하지 못했습니다.", e);
        }
    }
}
