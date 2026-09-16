package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.impl.ECDSA;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * KMS Sign API로 서명하는 {@link JWSSigner}.
 *
 * <p>개인키가 애플리케이션으로 오지 않는다는 점이 핵심이다. 서명할 데이터를 KMS에 보내고 서명값만
 * 돌려받는다(인증인가_설계서 1.3.2 — 개인키 외부 반출 금지).
 *
 * <p><b>다이제스트를 보낸다.</b> 원문 대신 SHA-256 해시를 보내면 네트워크로 나가는 양이 고정되고,
 * 토큰 본문(사용자 식별자 등)이 KMS 요청에 실리지 않는다.
 */
public class KmsEcdsaSigner implements JWSSigner {

    /** 설계서 1.3.1이 정한 기본 알고리즘. */
    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.ES256;
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final KmsClient kmsClient;
    private final String keyId;
    private final JCAContext jcaContext = new JCAContext();

    public KmsEcdsaSigner(KmsClient kmsClient, String keyId) {
        this.kmsClient = kmsClient;
        this.keyId = keyId;
    }

    @Override
    public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
        byte[] derSignature = kmsClient.sign(SignRequest.builder()
                        .keyId(keyId)
                        .messageType(MessageType.DIGEST)
                        .message(SdkBytes.fromByteArray(sha256(signingInput)))
                        .signingAlgorithm(SigningAlgorithmSpec.ECDSA_SHA_256)
                        .build())
                .signature()
                .asByteArray();

        // KMS는 DER(ASN.1 SEQUENCE)로 돌려주는데 JWS ES256은 64바이트 R‖S 원시 형식을 요구한다.
        // 이 변환을 빠뜨리면 서명이 만들어지기는 하지만 검증에서 조용히 실패한다.
        return Base64URL.encode(ECDSA.transcodeSignatureToConcat(
                derSignature, ECDSA.getSignatureByteArrayLength(ALGORITHM)));
    }

    private static byte[] sha256(byte[] input) throws JOSEException {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new JOSEException("SHA-256 다이제스트를 계산하지 못했습니다.", e);
        }
    }

    @Override
    public Set<JWSAlgorithm> supportedJWSAlgorithms() {
        return Set.of(ALGORITHM);
    }

    @Override
    public JCAContext getJCAContext() {
        return jcaContext;
    }
}
