package com.kurly.auth.infrastructure.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * refresh token 저장용 해시.
 * 인증인가_설계서 1.5 — 원본이 아닌 해시로 저장해 DB 유출 시에도 재사용을 막는다.
 *
 * <p>토큰은 서명된 JWT라 엔트로피가 충분하므로 SHA-256을 사용한다.
 * BCrypt는 저엔트로피 비밀번호용이고 입력이 72바이트에서 잘려 JWT에는 쓸 수 없다.
 */
@Component
public class RefreshTokenHasher {

    private static final String ALGORITHM = "SHA-256";

    public String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance(ALGORITHM)
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("%s 알고리즘을 사용할 수 없습니다.".formatted(ALGORITHM), e);
        }
    }
}
