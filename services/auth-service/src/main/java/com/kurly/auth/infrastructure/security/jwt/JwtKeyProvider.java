package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.JWKSet;

/**
 * JWT 서명키 공급자.
 * 운영에서는 KMS가 개인키를 보관하고 Sign API로만 서명하므로(인증인가_설계서 1.3.2),
 * 서명 수행과 공개키 노출만 노출하고 개인키 자체는 반환하지 않는다.
 */
public interface JwtKeyProvider {

    /** 현재 서명에 사용할 키의 kid. JWS 헤더에 실려 검증 측이 공개키를 고른다. */
    String activeKeyId();

    JWSSigner signer();

    /** JWKS로 배포할 공개키 집합. 키 회전 중에는 구 키가 함께 포함될 수 있다. */
    JWKSet publicJwkSet();
}
