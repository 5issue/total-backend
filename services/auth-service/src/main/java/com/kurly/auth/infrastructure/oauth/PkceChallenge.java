package com.kurly.auth.infrastructure.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE(RFC 7636) 값 생성.
 *
 * <p>인가 코드는 브라우저 리다이렉트 URL에 실려 오므로 히스토리·리퍼러·로그에 남는다.
 * {@code code_verifier}는 네트워크에 노출되지 않으므로, 코드를 탈취해도 토큰 교환을 할 수 없다.
 *
 * @param verifier  비밀값. 토큰 교환 시에만 전송한다
 * @param challenge 공개값. 인가 URL에 실린다
 */
public record PkceChallenge(String verifier, String challenge) {

    public static final String CHALLENGE_METHOD = "S256";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    /** RFC 7636은 verifier를 43~128자로 규정한다. 32바이트를 base64url로 인코딩하면 43자다. */
    private static final int VERIFIER_BYTES = 32;

    public static PkceChallenge generate() {
        byte[] raw = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(raw);
        String verifier = URL_ENCODER.encodeToString(raw);
        return new PkceChallenge(verifier, challengeOf(verifier));
    }

    private static String challengeOf(String verifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public static String generateState() {
        byte[] raw = new byte[VERIFIER_BYTES];
        RANDOM.nextBytes(raw);
        return URL_ENCODER.encodeToString(raw);
    }
}
