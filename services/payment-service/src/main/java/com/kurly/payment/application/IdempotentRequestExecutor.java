package com.kurly.payment.application;

import com.kurly.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * 멱등 요청 실행기.
 *
 * <p>같은 {@code Idempotency-Key}로 들어온 요청이 두 번 처리되지 않게 감싼다. 네트워크 재시도와
 * 사용자의 새로고침으로 중복 요청은 반드시 발생하고, 결제에서 중복 처리는 곧 이중 결제다.
 *
 * <p><b>실패했을 때 키를 어떻게 다룰지가 핵심이다.</b>
 * <ul>
 *   <li>{@link BusinessException} — 업무 규칙 위반이다. 금액 불일치·상태 오류·PG 거절 모두
 *       <b>돈이 움직이지 않은 것이 확실</b>하므로 선점을 풀어 같은 키로 재시도할 수 있게 한다.
 *   <li>그 밖의 예외 — PG 타임아웃처럼 <b>승인 여부를 알 수 없는</b> 상황이다. 선점을 유지해
 *       재시도를 막는다. 같은 키로 다시 오면 409로 응답한다. 매달린 기록은 대사 작업이 정리한다.
 * </ul>
 * 반대로 하면(모르는 실패에 선점을 풀면) 재시도가 이중 결제가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentRequestExecutor {

    private final IdempotencyService idempotencyService;
    private final JsonMapper jsonMapper;

    /**
     * @param successStatus 성공 시 응답 상태. 재요청에서 그대로 재생된다
     * @param action        실제 처리. 선점에 성공했을 때만 실행된다
     */
    public <T> Outcome<T> execute(Long userId, String key, String requestPath, Object request,
                                  Class<T> responseType, int successStatus, Supplier<T> action) {

        IdempotencyService.Result begun = idempotencyService.begin(
                userId, key, requestPath, fingerprint(jsonMapper.writeValueAsString(request)));

        if (begun.replay()) {
            log.info("멱등키로 저장된 응답을 재생한다: userId={}, path={}", userId, requestPath);
            return new Outcome<>(
                    begun.record().getResponseStatus(),
                    jsonMapper.readValue(begun.record().getResponseBody(), responseType),
                    true);
        }

        Long recordId = begun.record().getId();
        T result;
        try {
            result = action.get();
        } catch (BusinessException e) {
            // 돈이 움직이지 않은 것이 확실한 실패다. 고쳐서 다시 보낼 수 있어야 한다.
            idempotencyService.release(recordId);
            throw e;
        }

        idempotencyService.complete(recordId, successStatus, jsonMapper.writeValueAsString(result));
        return new Outcome<>(successStatus, result, false);
    }

    /**
     * 요청 본문의 지문.
     *
     * <p>본문 원문을 저장해 비교하지 않는 이유는 두 가지다. JSON 컬럼은 MySQL이 키 순서와 공백을
     * 정규화해 되읽은 값이 직렬화 원문과 절대 일치하지 않고, 결제 본문에는 PG 인증 토큰이 들어 있어
     * 원문을 남기지 않는 편이 안전하다.
     */
    private static String fingerprint(String requestBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(requestBody.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    /**
     * @param replayed 저장된 응답을 재생한 것인지. 실제 처리는 수행되지 않았다
     */
    public record Outcome<T>(int status, T body, boolean replayed) {
    }
}
