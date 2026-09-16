package com.kurly.auth.infrastructure.config;

import com.kurly.auth.infrastructure.security.jwt.JwtKeyProvider;
import com.kurly.auth.infrastructure.security.jwt.KmsJwtKeyProvider;
import com.kurly.auth.infrastructure.security.jwt.KmsKeyProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.KmsClientBuilder;

/**
 * KMS 기반 서명키 구성. {@code jwt.key-provider=kms}일 때만 활성화된다.
 *
 * <p>로컬·테스트는 {@code local} 기본값으로 두어 AWS 없이 기동된다. 운영에서만 KMS로 바꾼다.
 */
@Configuration
@EnableConfigurationProperties(KmsKeyProperties.class)
@ConditionalOnProperty(name = "jwt.key-provider", havingValue = "kms")
public class KmsConfig {

    /**
     * 자격증명은 기본 조회 순서를 그대로 쓴다. EKS에서는 IRSA가 주입한 웹 아이덴티티 토큰을
     * 자동으로 집으므로 정적 키를 넣을 필요가 없다(설계서 3.5 — 클러스터는 EKS로 확정).
     *
     * <p>HTTP 클라이언트는 JDK 기본 커넥션을 쓰는 경량 구현을 명시한다. 서명은 토큰 발급 시점의
     * 저빈도 동기 호출이라 비동기 스택이 필요 없고, 의존성에서 Netty를 뺐기 때문에 기본값으로
     * 두면 클라이언트를 찾지 못한다.
     */
    @Bean
    public KmsClient kmsClient(KmsKeyProperties properties) {
        KmsClientBuilder builder = KmsClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder());
        if (StringUtils.hasText(properties.region())) {
            builder.region(Region.of(properties.region()));
        }
        return builder.build();
    }

    @Bean
    public JwtKeyProvider jwtKeyProvider(KmsClient kmsClient, KmsKeyProperties properties) {
        return new KmsJwtKeyProvider(kmsClient, properties);
    }
}
