package com.kurly.auth.infrastructure.security.jwt;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;
import software.amazon.awssdk.services.kms.model.KmsException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

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

    private void givenPublicKeyFor(String... arns) {
        given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class))).willAnswer(invocation -> {
            GetPublicKeyRequest request = invocation.getArgument(0);
            String arn = java.util.Arrays.stream(arns)
                    .filter(candidate -> candidate.contains(request.keyId()) || request.keyId().equals(candidate))
                    .findFirst()
                    .orElse(request.keyId());
            return GetPublicKeyResponse.builder()
                    .keyId(arn)
                    .publicKey(SdkBytes.fromByteArray(publicKeyDer))
                    .build();
        });
    }

    private static KmsKeyProperties properties(String keyId, String previousKeyId) {
        return new KmsKeyProperties(keyId, previousKeyId, "ap-northeast-2");
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
        void EC가_아닌_키면_사양을_알려주며_막는다() {
            // RSA 키를 잘못 지정하는 실수를 런타임이 아니라 기동에서 잡는다.
            given(kmsClient.getPublicKey(any(GetPublicKeyRequest.class)))
                    .willReturn(GetPublicKeyResponse.builder()
                            .keyId(CURRENT_ARN)
                            .publicKey(SdkBytes.fromByteArray(new byte[]{1, 2, 3}))
                            .build());

            assertThatThrownBy(() -> new KmsJwtKeyProvider(kmsClient, properties(CURRENT_ARN, null)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ECC_NIST_P256");
        }
    }
}
