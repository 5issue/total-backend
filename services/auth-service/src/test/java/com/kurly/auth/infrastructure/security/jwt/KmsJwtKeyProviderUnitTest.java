package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.KeySpec;
import software.amazon.awssdk.services.kms.model.KeyUsageType;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class KmsJwtKeyProviderUnitTest {

    private static final String CURRENT_ARN =
            "arn:aws:kms:ap-northeast-2:111122223333:key/11111111-2222-3333-4444-555555555555";
    private static final String PREVIOUS_ARN =
            "arn:aws:kms:ap-northeast-2:111122223333:key/99999999-8888-7777-6666-555555555555";

    @Mock KmsClient kmsClient;

    private byte[] publicKeyDer;

    @BeforeEach
    void generateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        // KMS GetPublicKey가 돌려주는 형식과 같다(DER SubjectPublicKeyInfo).
        publicKeyDer = keyPair.getPublic().getEncoded();
    }

    /** 정상적인 ES256 서명키 응답. KMS가 돌려주는 메타데이터를 함께 채운다. */
    private GetPublicKeyResponse.Builder validSigningKey(String arn) {
        return GetPublicKeyResponse.builder()
                .keyId(arn)
                .keySpec(KeySpec.ECC_NIST_P256)
                .keyUsage(KeyUsageType.SIGN_VERIFY)
                .signingAlgorithms(SigningAlgorithmSpec.ECDSA_SHA_256)
                .publicKey(SdkBytes.fromByteArray(publicKeyDer));
    }

    private void givenPublicKeyFor(String... arns) {
        given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class))).willAnswer(invocation -> {
            GetPublicKeyRequest request = invocation.getArgument(0);
            String arn = java.util.Arrays.stream(arns)
                    .filter(candidate -> candidate.contains(request.keyId()) || request.keyId().equals(candidate))
                    .findFirst()
                    .orElse(request.keyId());
            return validSigningKey(arn).build();
        });
    }

    private static KmsKeyProperties properties(String keyId, String previousKeyId) {
        return new KmsKeyProperties(keyId, previousKeyId, "ap-northeast-2");
    }

    /** 서명 요청 대상을 확인하기 위해 한 번 서명한다. 서명값 자체는 보지 않는다. */
    private static void signOnce(KmsJwtKeyProvider provider) throws Exception {
        com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader.Builder(JWSAlgorithm.ES256)
                        .keyID(provider.activeKeyId()).build(),
                new com.nimbusds.jwt.JWTClaimsSet.Builder().subject("1").build());
        jwt.sign(provider.signer());
    }

    /** KMS가 돌려주는 형식과 같은 DER 서명. 내용은 검증하지 않으므로 형식만 맞춘다. */
    private byte[] derSignature() throws Exception {
        java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        java.security.Signature signature = java.security.Signature.getInstance("NONEwithECDSA");
        signature.initSign(generator.generateKeyPair().getPrivate());
        signature.update(new byte[32]);
        return signature.sign();
    }

    @Nested
    @DisplayName("공개키 배포")
    class PublicKeyTest {

        @Test
        void ARN에서_kid를_뽑아_JWKS와_서명_헤더를_맞춘다() {
            // 설정에 ARN을 넣어도 kid는 키 식별자만 써야 한다. 둘이 어긋나면 검증 측이 키를 못 고른다.
            givenPublicKeyFor(CURRENT_ARN);

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null));

            assertThat(provider.activeKeyId()).isEqualTo("11111111-2222-3333-4444-555555555555");
            assertThat(provider.publicJwkSet().getKeys())
                    .singleElement()
                    .extracting(JWK::getKeyID)
                    .isEqualTo(provider.activeKeyId());
        }

        @Test
        void 공개키만_노출한다() {
            // 개인키가 JWKS로 새어나가면 KMS를 쓰는 의미가 사라진다.
            givenPublicKeyFor(CURRENT_ARN);

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null));

            assertThat(provider.publicJwkSet().getKeys()).allMatch(jwk -> !jwk.isPrivate());
        }

        @Test
        void ES256_키로_배포한다() {
            givenPublicKeyFor(CURRENT_ARN);

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null));

            assertThat(provider.publicJwkSet().getKeys())
                    .singleElement()
                    .extracting(JWK::getAlgorithm)
                    .isEqualTo(JWSAlgorithm.ES256);
        }
    }

    @Nested
    @DisplayName("키 회전 — 구 키 병행")
    class RotationTest {

        @Test
        void 이전_키를_설정하면_JWKS에_함께_싣는다() {
            // 설계서 1.3.2: 회전 시 구 키 7일 병행. 구 키로 서명된 토큰이 살아 있는 동안
            // 검증이 깨지면 안 된다.
            givenPublicKeyFor(CURRENT_ARN, PREVIOUS_ARN);

            KmsJwtKeyProvider provider =
                    new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, PREVIOUS_ARN));

            assertThat(provider.publicJwkSet().getKeys()).hasSize(2);
        }

        @Test
        void 서명은_언제나_현재_키로만_한다() {
            givenPublicKeyFor(CURRENT_ARN, PREVIOUS_ARN);

            KmsJwtKeyProvider provider =
                    new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, PREVIOUS_ARN));

            assertThat(provider.activeKeyId()).isEqualTo("11111111-2222-3333-4444-555555555555");
        }

        @Test
        void 이전_키가_없으면_현재_키만_싣는다() {
            givenPublicKeyFor(CURRENT_ARN);

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, ""));

            assertThat(provider.publicJwkSet().getKeys()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("서명 대상 고정 — 별칭 재지정 대비")
    class SigningTargetTest {

        private static final String ALIAS = "alias/kurly-jwt-signing";

        @Test
        void 별칭으로_설정해도_해석된_ARN으로_서명한다() throws Exception {
            // 별칭을 그대로 들고 있으면, 운영 중 별칭이 새 키를 가리키는 순간 서명은 새 키로
            // 나가는데 kid·JWKS는 기동 시 캐시한 옛 키를 가리킨다. 그 토큰은 검증에 실패한다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN).build());
            given(kmsClient.sign(any(SignRequest.class)))
                    .willReturn(SignResponse.builder()
                            .signature(SdkBytes.fromByteArray(derSignature()))
                            .build());

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(ALIAS, null));
            signOnce(provider);

            ArgumentCaptor<SignRequest> captor = ArgumentCaptor.forClass(SignRequest.class);
            verify(kmsClient).sign(captor.capture());
            assertThat(captor.getValue().keyId())
                    .isEqualTo(CURRENT_ARN)
                    .isNotEqualTo(ALIAS);
        }

        @Test
        void 서명_대상은_kid가_아니라_ARN이다() throws Exception {
            // kid는 ARN의 마지막 식별자만 잘라낸 값이라 계정·리전 정보가 없다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN).build());
            given(kmsClient.sign(any(SignRequest.class)))
                    .willReturn(SignResponse.builder()
                            .signature(SdkBytes.fromByteArray(derSignature()))
                            .build());

            KmsJwtKeyProvider provider = new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null));
            signOnce(provider);

            ArgumentCaptor<SignRequest> captor = ArgumentCaptor.forClass(SignRequest.class);
            verify(kmsClient).sign(captor.capture());
            assertThat(captor.getValue().keyId()).isNotEqualTo(provider.activeKeyId());
        }
    }

    @Nested
    @DisplayName("키 사양 검증")
    class KeySpecTest {

        @Test
        void P256이_아니면_기동을_막는다() {
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN).keySpec(KeySpec.RSA_2048).build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ECC_NIST_P256");
        }

        @Test
        void 서명용이_아닌_키는_기동을_막는다() {
            // KEY_AGREEMENT 키는 공개키 파싱과 JWKS 구성을 통과한 뒤 첫 서명에서야 실패한다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN).keyUsage(KeyUsageType.KEY_AGREEMENT).build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SIGN_VERIFY");
        }

        @Test
        void ECDSA_SHA_256을_지원하지_않으면_기동을_막는다() {
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN)
                            .signingAlgorithms(SigningAlgorithmSpec.ECDSA_SHA_512).build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ECDSA_SHA_256");
        }

        @Test
        void 이전_키에는_서명_용도를_요구하지_않는다() {
            // 이전 키는 검증용으로만 게시한다. KMS 서명 메타데이터까지 요구할 이유가 없다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class))).willAnswer(invocation -> {
                GetPublicKeyRequest request = invocation.getArgument(0);
                return CURRENT_ARN.equals(request.keyId())
                        ? validSigningKey(CURRENT_ARN).build()
                        : validSigningKey(PREVIOUS_ARN)
                                .keyUsage(KeyUsageType.KEY_AGREEMENT)
                                .signingAlgorithms(java.util.List.of())
                                .build();
            });

            KmsJwtKeyProvider provider =
                    new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, PREVIOUS_ARN));

            assertThat(provider.publicJwkSet().getKeys()).hasSize(2);
        }

        @Test
        void 이전_키도_P256은_요구한다() {
            // P-256이 아니면 애초에 ES256용 JWK를 만들 수 없다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class))).willAnswer(invocation -> {
                GetPublicKeyRequest request = invocation.getArgument(0);
                return CURRENT_ARN.equals(request.keyId())
                        ? validSigningKey(CURRENT_ARN).build()
                        : validSigningKey(PREVIOUS_ARN).keySpec(KeySpec.RSA_2048).build();
            });

            assertThatThrownBy(() ->
                    new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, PREVIOUS_ARN)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("이전");
        }
    }

    @Nested
    @DisplayName("기동 실패")
    class StartupFailureTest {

        @Test
        void KMS_조회가_실패하면_기동을_막는다() {
            // 공개키를 배포하지 못하는 auth-service는 전 서비스의 토큰 검증을 멈추게 한다.
            // 포트를 열어두고 실패를 흘리는 것보다 뜨지 않는 편이 낫다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willThrow(KmsException.builder().message("AccessDenied").build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("공개키를 받지 못했습니다");
        }

        @Test
        void 공개키_바이트가_깨졌으면_기동을_막는다() {
            // 사양은 맞는데 본문이 해석되지 않는 경우다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(validSigningKey(CURRENT_ARN)
                            .publicKey(SdkBytes.fromByteArray(new byte[]{1, 2, 3})).build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EC JWK로 변환하지 못했습니다");
        }
    }
}
