package com.kurly.product.presentation.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MediaUrlEncoderTest {

    @Test
    @DisplayName("실제 CDN 경로에 있는 인코딩 안 된 특수문자(^, >)를 퍼센트 인코딩해서 유효한 URI로 만든다")
    void encodesInvalidCharactersInRealCdnPath() {
        String raw = "https://product-image.kurly.com/hdims/resize/^>720x>936/cropcenter/720x936/"
                + "quality/85/src/product/image/5588e602-4670-4eba-80bd-0d8690493ad3.jpg";

        String encoded = MediaUrlEncoder.encode(raw);

        assertThatIsValidUri(encoded);
        assertThat(encoded).contains("%5E").contains("%3E");
    }

    @Test
    @DisplayName("이미 유효한 URL은 구조를 바꾸지 않는다")
    void leavesAlreadyValidUrlUnchanged() {
        String raw = "https://img-cf.kurly.com/shop/data/goods/163001/001.jpg";

        String encoded = MediaUrlEncoder.encode(raw);

        assertThat(encoded).isEqualTo(raw);
        assertThatIsValidUri(encoded);
    }

    @Test
    @DisplayName("이미 퍼센트 인코딩된 구간(%20)은 다시 인코딩하지 않는다(이중 인코딩 방지)")
    void doesNotDoubleEncodeAlreadyEncodedSegment() {
        String alreadyEncoded = "https://img-cf.kurly.com/shop/data/goods/163001/my%20image.jpg";

        String encoded = MediaUrlEncoder.encode(alreadyEncoded);

        assertThat(encoded).isEqualTo(alreadyEncoded);
        assertThat(encoded).doesNotContain("%2520");
    }

    @Test
    @DisplayName("인코딩 안 된 raw 공백은 %20으로 인코딩된다")
    void encodesRawSpace() {
        String raw = "https://img-cf.kurly.com/shop/data/goods/163001/my image.jpg";

        String encoded = MediaUrlEncoder.encode(raw);

        assertThat(encoded).isEqualTo("https://img-cf.kurly.com/shop/data/goods/163001/my%20image.jpg");
        assertThatIsValidUri(encoded);
    }

    @Test
    @DisplayName("이미 인코딩된 구간과 인코딩 안 된 특수문자가 섞여 있어도 각각 올바르게 처리한다")
    void handlesMixOfEncodedAndUnencodedSegments() {
        String mixed = "https://product-image.kurly.com/hdims/resize/^>720x>936/my%20file.jpg";

        String encoded = MediaUrlEncoder.encode(mixed);

        assertThat(encoded).contains("%5E").contains("%3E");
        assertThat(encoded).contains("my%20file.jpg");
        assertThat(encoded).doesNotContain("%2520");
        assertThatIsValidUri(encoded);
    }

    @Test
    @DisplayName("파일명 안의 %2F는 경로 구분자로 바뀌지 않고 그대로 %2F로 남는다")
    void preservesEncodedSlashInFilename() {
        String withEncodedSlash = "https://img-cf.kurly.com/shop/data/goods/163001/a%2Fb.jpg";

        String encoded = MediaUrlEncoder.encode(withEncodedSlash);

        assertThat(encoded).contains("a%2Fb.jpg");
    }

    @Test
    @DisplayName("파일명 안의 %23은 fragment 구분자로 바뀌지 않고 그대로 %23으로 남는다")
    void preservesEncodedHashInFilename() {
        String withEncodedHash = "https://img-cf.kurly.com/shop/data/goods/163001/a%23b.jpg";

        String encoded = MediaUrlEncoder.encode(withEncodedHash);

        assertThat(encoded).contains("a%23b.jpg");
    }

    @Test
    @DisplayName("null/blank은 그대로 반환한다")
    void passesThroughNullAndBlank() {
        assertThat(MediaUrlEncoder.encode(null)).isNull();
        assertThat(MediaUrlEncoder.encode("")).isEmpty();
    }

    private void assertThatIsValidUri(String value) {
        try {
            new URI(value);
        } catch (Exception e) {
            throw new AssertionError("유효한 URI가 아닙니다: " + value, e);
        }
    }
}
