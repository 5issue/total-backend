package com.kurly.product.presentation.support;

import java.nio.charset.StandardCharsets;

/**
 * DB에 저장된 media_url이 전부 RFC 3986 유효 URI는 아니다(예: 실제 CDN 리사이즈 경로에
 * {@code ^}, {@code >} 같은 인코딩 안 된 문자가 그대로 들어있는 경우가 있음 — {@code new
 * URI(url)}로 파싱하면 URISyntaxException). 응답에 내보내기 전에 불법 문자만 퍼센트 인코딩해서
 * 항상 유효한 URI가 되도록 한다.
 * <p>
 * 문자열을 통째로 디코딩했다가 다시 인코딩하는 방식은 쓰지 않는다 — 그렇게 하면 파일명 안에
 * 의도적으로 인코딩해 둔 {@code %2F}(경로 구분자 아님), {@code %23}(fragment 구분자 아님) 같은
 * 예약 문자가 디코딩되면서 실제 경로 구분자/fragment 구분자로 둔갑해 URI 구조 자체가 깨진다.
 * 그래서 이미 유효한 {@code %XX} 시퀀스는 건드리지 않고 그대로 통과시키고, 그 외의 불법 문자만
 * 퍼센트 인코딩하는 방식으로 한 글자씩 스캔한다.
 */
public final class MediaUrlEncoder {

    /** RFC 3986 unreserved + reserved(gen-delims/sub-delims) 문자. 인코딩하지 않고 그대로 둔다. */
    private static final String SAFE_CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
            + ":/?#[]@!$&'()*+,;=";

    private MediaUrlEncoder() {
    }

    public static String encode(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }

        StringBuilder result = new StringBuilder(url.length());
        int i = 0;
        while (i < url.length()) {
            char c = url.charAt(i);
            if (c == '%' && isPercentEncodedTriplet(url, i)) {
                result.append(url, i, i + 3);
                i += 3;
                continue;
            }
            if (SAFE_CHARACTERS.indexOf(c) >= 0) {
                result.append(c);
                i++;
                continue;
            }
            int codePoint = url.codePointAt(i);
            for (byte b : new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8)) {
                result.append('%').append(String.format("%02X", b));
            }
            i += Character.charCount(codePoint);
        }
        return result.toString();
    }

    private static boolean isPercentEncodedTriplet(String url, int index) {
        return index + 2 < url.length()
                && isHexDigit(url.charAt(index + 1))
                && isHexDigit(url.charAt(index + 2));
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f');
    }
}
