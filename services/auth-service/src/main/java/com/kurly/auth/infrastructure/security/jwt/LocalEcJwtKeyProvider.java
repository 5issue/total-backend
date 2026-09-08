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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.util.UUID;

/**
 * 로컬·개발 환경용 EC P-256 서명키 공급자.
 * 운영에서는 KMS 기반 구현으로 교체한다(인증인가_설계서 1.3.2 — 개인키 외부 반출 금지).
 */
@Slf4j
@Component
public class LocalEcJwtKeyProvider implements JwtKeyProvider {

    private final ECKey ecKey;
    private final JWSSigner signer;

    public LocalEcJwtKeyProvider(JwtProperties properties) {
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
        if (StringUtils.hasText(privateJwk)) {
            try {
                return ECKey.parse(privateJwk);
            } catch (ParseException e) {
                throw new IllegalStateException("jwt.private-jwk 값을 EC JWK로 해석하지 못했습니다.", e);
            }
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
