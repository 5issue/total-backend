package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * KMS가 개인키를 보관하는 서명키 공급자(인증인가_설계서 1.3.2).
 *
 * <p>개인키는 KMS 밖으로 나오지 않는다. 이 클래스가 다루는 것은 <b>서명을 요청할 대상(keyId)</b>과
 * <b>검증 측에 배포할 공개키</b>뿐이다.
 *
 * <h2>키 회전</h2>
 * 설계서 1.3.2는 6개월 주기 수동 회전과 <b>구 키 7일 병행</b>으로 무중단 전환을 규정한다.
 * 그래서 JWKS에는 현재 키와 이전 키를 <b>함께</b> 싣는다. 구 키로 서명된 토큰이 아직 살아 있는
 * 동안 검증이 깨지지 않도록 하기 위함이다. 서명은 언제나 현재 키로만 한다.
 *
 * <p>회전 절차: 새 키 생성 → {@code previous-key-id}에 구 키를 넣고 {@code key-id}를 새 키로 교체 →
 * 배포 → 7일 경과(refresh 최대 수명 이상) → {@code previous-key-id} 제거 → 배포.
 *
 * <h2>공개키 조회 시점</h2>
 * 공개키는 키마다 고정이라 <b>기동 시 한 번 받아 캐시한다.</b> 매 요청마다 KMS를 부르면 JWKS
 * 엔드포인트가 KMS 지연·장애에 그대로 묶인다. 대신 기동 시 받지 못하면 <b>기동을 중단</b>한다 —
 * 공개키를 배포하지 못하는 auth-service는 전 서비스의 토큰 검증을 멈추게 하므로, 포트를 열어두고
 * 실패를 흘리는 것보다 뜨지 않는 편이 낫다.
 */
@Slf4j
public class KmsJwtKeyProvider implements JwtKeyProvider {

    /** ARN에서 키 식별자를 잘라내는 구분자. {@code arn:...:key/<uuid>} 형태다. */
    private static final String ARN_KEY_SEPARATOR = "key/";

    private final String activeKeyId;
    private final String signingKeyReference;
    private final JWKSet publicJwkSet;
    private final JWSSigner signer;

    public KmsJwtKeyProvider(KmsClient kmsClient, KmsKeyProperties properties) {
        this.signingKeyReference = properties.keyId();
        this.signer = new KmsEcdsaSigner(kmsClient, signingKeyReference);

        JWK current = fetchPublicJwk(kmsClient, signingKeyReference, "현재");
        this.activeKeyId = current.getKeyID();

        List<JWK> keys = new ArrayList<>();
        keys.add(current);
        if (properties.hasPreviousKey()) {
            // 회전 중이다. 구 키로 서명된 토큰이 만료될 때까지 검증이 가능해야 한다.
            keys.add(fetchPublicJwk(kmsClient, properties.previousKeyId(), "이전"));
        }
        this.publicJwkSet = new JWKSet(keys);

        log.info("KMS 서명키 준비 완료: activeKid={}, JWKS 키 수={}", activeKeyId, keys.size());
    }

    @Override
    public String activeKeyId() {
        return activeKeyId;
    }

    @Override
    public JWSSigner signer() {
        return signer;
    }

    @Override
    public JWKSet publicJwkSet() {
        return publicJwkSet;
    }

    /**
     * KMS에서 공개키를 받아 JWK로 만든다.
     *
     * <p>{@code kid}는 <b>설정값이 아니라 KMS가 돌려준 키 ARN에서 뽑는다.</b> 별칭(alias)으로
     * 설정했더라도 실제 키를 가리키는 값이 되어, JWS 헤더의 {@code kid}와 JWKS가 어긋나지 않는다.
     */
    private static JWK fetchPublicJwk(KmsClient kmsClient, String keyReference, String label) {
        GetPublicKeyResponse response;
        try {
            response = kmsClient.getPublicKey(GetPublicKeyRequest.builder().keyId(keyReference).build());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "KMS에서 %s 공개키를 받지 못했습니다: %s".formatted(label, keyReference), e);
        }

        String kid = toKeyId(response.keyId());
        try {
            // KMS는 DER(SubjectPublicKeyInfo)로 준다. JDK 표준 스펙으로 해석한다.
            ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(response.publicKey().asByteArray()));
            return new ECKey.Builder(Curve.P_256, publicKey)
                    .keyID(kid)
                    .algorithm(JWSAlgorithm.ES256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "KMS %s 공개키를 EC JWK로 변환하지 못했습니다. 키 사양이 ECC_NIST_P256인지 확인하세요: %s"
                            .formatted(label, keyReference), e);
        }
    }

    /** ARN이면 마지막 식별자만, 아니면 그대로 쓴다. ARN 전체를 kid로 쓰기엔 길다. */
    private static String toKeyId(String keyArn) {
        int index = keyArn.lastIndexOf(ARN_KEY_SEPARATOR);
        return index < 0 ? keyArn : keyArn.substring(index + ARN_KEY_SEPARATOR.length());
    }
}
