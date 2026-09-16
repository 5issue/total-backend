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
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class KmsJwtKeyProvider implements JwtKeyProvider {

    /** ARN에서 키 식별자를 잘라내는 구분자. {@code arn:...:key/<uuid>} 형태다. */
    private static final String ARN_KEY_SEPARATOR = "key/";

    private final String activeKeyId;
    private final JWKSet publicJwkSet;
    private final JWSSigner signer;

    public KmsJwtKeyProvider(KmsClient kmsClient, KmsKeyProperties properties) {
        ResolvedKey current = fetchKey(kmsClient, properties.keyId(), "현재", true);
        this.activeKeyId = current.jwk().getKeyID();

        // 설정값(별칭일 수 있다)이 아니라 해석된 ARN으로 서명한다. 위 "서명 대상 고정" 참조.
        this.signer = new KmsEcdsaSigner(kmsClient, current.arn());

        List<JWK> keys = new ArrayList<>();
        keys.add(current.jwk());
        if (properties.hasPreviousKey()) {
            // 회전 중이다. 구 키로 서명된 토큰이 만료될 때까지 검증이 가능해야 한다.
            // 서명에는 쓰지 않으므로 KMS 서명 용도까지 요구하지는 않는다.
            keys.add(fetchKey(kmsClient, properties.previousKeyId(), "이전", false).jwk());
        }
        this.publicJwkSet = new JWKSet(keys);

        log.info("KMS 서명키 준비 완료: activeKid={}, 서명 대상={}, JWKS 키 수={}",
                activeKeyId, current.arn(), keys.size());
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

    private record ResolvedKey(String arn, JWK jwk) {
    }

    private static ResolvedKey fetchKey(KmsClient kmsClient, String keyReference,
                                        String label, boolean forSigning) {
        GetPublicKeyResponse response;
        try {
            response = kmsClient.getPublicKey(GetPublicKeyRequest.builder().keyId(keyReference).build());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "KMS에서 %s 공개키를 받지 못했습니다: %s".formatted(label, keyReference), e);
        }

        verifySpec(response, keyReference, label, forSigning);
        return new ResolvedKey(response.keyId(), toJwk(response, keyReference, label));
    }

    private static void verifySpec(GetPublicKeyResponse response, String keyReference,
                                   String label, boolean forSigning) {
        // 곡선은 두 키 모두 확인한다. P-256이 아니면 ES256용 JWK를 만들 수 없다.
        if (response.keySpec() != KeySpec.ECC_NIST_P256) {
            throw new IllegalStateException(
                    "KMS %s 키는 ES256용 ECC_NIST_P256이어야 합니다. 현재 사양=%s, 키=%s"
                            .formatted(label, response.keySpecAsString(), keyReference));
        }
        if (!forSigning) {
            return;
        }
        if (response.keyUsage() != KeyUsageType.SIGN_VERIFY) {
            throw new IllegalStateException(
                    "KMS %s 키의 용도가 SIGN_VERIFY여야 서명할 수 있습니다. 현재 용도=%s, 키=%s"
                            .formatted(label, response.keyUsageAsString(), keyReference));
        }
        if (!response.signingAlgorithms().contains(SigningAlgorithmSpec.ECDSA_SHA_256)) {
            throw new IllegalStateException(
                    "KMS %s 키가 ECDSA_SHA_256을 지원하지 않습니다. 지원 목록=%s, 키=%s"
                            .formatted(label, response.signingAlgorithmsAsStrings(), keyReference));
        }
    }

    private static JWK toJwk(GetPublicKeyResponse response, String keyReference, String label) {
        try {
            // KMS는 DER(SubjectPublicKeyInfo)로 준다. JDK 표준 스펙으로 해석한다.
            ECPublicKey publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new X509EncodedKeySpec(response.publicKey().asByteArray()));
            return new ECKey.Builder(Curve.P_256, publicKey)
                    .keyID(toKeyId(response.keyId()))
                    .algorithm(JWSAlgorithm.ES256)
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "KMS %s 공개키를 EC JWK로 변환하지 못했습니다: %s".formatted(label, keyReference), e);
        }
    }

    private static String toKeyId(String keyArn) {
        int index = keyArn.lastIndexOf(ARN_KEY_SEPARATOR);
        return index < 0 ? keyArn : keyArn.substring(index + ARN_KEY_SEPARATOR.length());
    }
}
