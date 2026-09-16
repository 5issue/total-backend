package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * KMS 서명이 <b>실제로 검증을 통과하는지</b>를 확인한다.
 *
 * <p>KMS는 서명을 DER(ASN.1)로 돌려주는데 JWS ES256은 64바이트 R‖S 원시 형식을 요구한다. 변환을
 * 빠뜨려도 서명 자체는 만들어지므로 <b>토큰 발급은 성공하고 검증에서만 실패한다.</b> 단정을
 * "예외가 안 난다"에 두면 이 결함이 통과한다. 그래서 여기서는 끝까지 검증한다.
 *
 * <p>AWS 없이 돌린다. KMS의 {@code DIGEST} 서명은 "받은 다이제스트를 그대로 ECDSA 서명"이므로
 * JCA의 {@code NONEwithECDSA}로 같은 동작을 재현할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
class KmsEcdsaSignerUnitTest {

    private static final String KEY_ID = "arn:aws:kms:ap-northeast-2:111122223333:key/test-key";

    @Mock KmsClient kmsClient;

    private KeyPair keyPair;

    @BeforeEach
    void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        keyPair = generator.generateKeyPair();
    }

    /** KMS를 대신해 같은 알고리즘으로 서명한다. 돌려주는 형식도 KMS와 같은 DER이다. */
    private void givenKmsSignsWithLocalKey() {
        given(kmsClient.sign(any(SignRequest.class))).willAnswer(invocation -> {
            SignRequest request = invocation.getArgument(0);
            assertThat(request.messageType()).isEqualTo(MessageType.DIGEST);
            assertThat(request.signingAlgorithm()).isEqualTo(SigningAlgorithmSpec.ECDSA_SHA_256);

            Signature signature = Signature.getInstance("NONEwithECDSA");
            signature.initSign((ECPrivateKey) keyPair.getPrivate());
            signature.update(request.message().asByteArray());
            return SignResponse.builder()
                    .signature(SdkBytes.fromByteArray(signature.sign()))
                    .build();
        });
    }

    private SignedJWT signToken() throws Exception {
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.ES256).keyID("kid-1").type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder().subject("42").issuer("https://auth.kurly.local").build());
        jwt.sign(new KmsEcdsaSigner(kmsClient, KEY_ID));
        return jwt;
    }

    @Nested
    @DisplayName("서명")
    class SignTest {

        @Test
        void KMS가_돌려준_DER_서명이_JWS_검증을_통과한다() throws Exception {
            // 변환을 빠뜨리면 여기서만 드러난다. 발급 자체는 성공하기 때문이다.
            givenKmsSignsWithLocalKey();

            SignedJWT jwt = signToken();

            assertThat(jwt.verify(new ECDSAVerifier((ECPublicKey) keyPair.getPublic()))).isTrue();
        }

        @Test
        void 서명값이_ES256_규격인_64바이트다() throws Exception {
            // DER은 길이가 들쭉날쭉하다. 64바이트 고정이어야 원시 R‖S로 변환된 것이다.
            givenKmsSignsWithLocalKey();

            assertThat(signToken().getSignature().decode()).hasSize(64);
        }

        @Test
        void 다른_키의_공개키로는_검증에_실패한다() throws Exception {
            // 위 검증이 "무조건 true"가 아님을 확인한다.
            givenKmsSignsWithLocalKey();
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            ECPublicKey otherKey = (ECPublicKey) generator.generateKeyPair().getPublic();

            assertThat(signToken().verify(new ECDSAVerifier(otherKey))).isFalse();
        }
    }

    @Nested
    @DisplayName("KMS 요청 구성")
    class RequestTest {

        @Test
        void 원문이_아니라_다이제스트를_보낸다() throws Exception {
            // 토큰 본문(사용자 식별자 등)이 KMS 요청에 실리지 않아야 한다.
            givenKmsSignsWithLocalKey();

            SignedJWT jwt = signToken();

            org.mockito.ArgumentCaptor<SignRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(SignRequest.class);
            org.mockito.Mockito.verify(kmsClient).sign(captor.capture());
            byte[] sent = captor.getValue().message().asByteArray();
            assertThat(sent).hasSize(32);                       // SHA-256 다이제스트 길이
            assertThat(new String(sent)).doesNotContain("42");  // 원문 클레임이 실리지 않는다
            assertThat(jwt.getSignature()).isNotNull();
        }

        @Test
        void 설정된_키를_지정해_서명을_요청한다() throws Exception {
            givenKmsSignsWithLocalKey();

            signToken();

            org.mockito.ArgumentCaptor<SignRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(SignRequest.class);
            org.mockito.Mockito.verify(kmsClient).sign(captor.capture());
            assertThat(captor.getValue().keyId()).isEqualTo(KEY_ID);
        }
    }

    @Nested
    @DisplayName("알고리즘 선언")
    class AlgorithmTest {

        @Test
        void ES256만_지원한다고_알린다() {
            // Nimbus가 헤더 알고리즘과 대조한다. 넓게 선언하면 맞지 않는 알고리즘으로 서명을 시도한다.
            assertThat(new KmsEcdsaSigner(kmsClient, KEY_ID).supportedJWSAlgorithms())
                    .containsExactly(JWSAlgorithm.ES256);
        }
    }
}
