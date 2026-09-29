package com.kurly.product.presentation.support;

import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * DB에 저장된 media_url이 전부 RFC 3986 유효 URI인 건 아니다(예: 실제 CDN 리사이즈 경로에
 * {@code ^}, {@code >} 같은 인코딩 안 된 문자가 그대로 들어있는 경우가 있음 — {@code new
 * URI(url)}로 파싱하면 URISyntaxException). 응답에 내보내기 전에 퍼센트 인코딩해서 항상 유효한
 * URI가 되도록 한다.
 * <p>
 * 일부 값은 이미 퍼센트 인코딩된 채로 저장돼 있을 수 있는데, 무작정 다시 인코딩하면
 * {@code UriComponentsBuilder.encode()}가 기존 {@code %XX} 시퀀스의 {@code %}까지 또
 * 인코딩해버려 {@code %20 -> %2520}처럼 이중 인코딩된다. 그래서 먼저 디코딩해서 "원래 의도한
 * 원문"으로 되돌린 뒤 다시 인코딩한다 — 입력이 raw든 이미 인코딩돼 있든 항상 같은 결과가 나온다.
 * 디코딩이 실패하면(잘못된 {@code %} 시퀀스 등) 원문을 그대로 인코딩 단계로 넘긴다.
 */
@Slf4j
public final class MediaUrlEncoder {

    private MediaUrlEncoder() {
    }

    public static String encode(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            return UriComponentsBuilder.fromUriString(normalize(url))
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
        } catch (RuntimeException e) {
            log.warn("media_url을 URI로 인코딩하지 못해 원본 값을 그대로 반환합니다: {}", url, e);
            return url;
        }
    }

    private static String normalize(String url) {
        try {
            return UriUtils.decode(url, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return url;
        }
    }
}
