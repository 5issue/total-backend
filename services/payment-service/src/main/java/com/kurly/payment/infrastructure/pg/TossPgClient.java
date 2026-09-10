package com.kurly.payment.infrastructure.pg;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.payment.application.port.PgClient;
import com.kurly.payment.exception.PaymentDeclinedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.kurly.payment.infrastructure.client.OutboundRestClients;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 토스페이먼츠 연동.
 *
 * <p>인증은 Basic이며 <b>시크릿 키 뒤에 콜론을 붙여</b> Base64로 인코딩한다. 콜론을 빼면 인증이
 * 실패한다(토스 개발자센터 인증 가이드).
 *
 * <p>모든 POST 요청에 {@code Idempotency-Key}를 실어 재시도가 이중 결제·이중 취소가 되지 않게 한다.
 * 우리 쪽 재시도 배치가 같은 취소를 여러 번 부를 수 있으므로 특히 취소에서 중요하다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.pg.client", havingValue = "toss", matchIfMissing = true)
public class TossPgClient implements PgClient {

    private static final String CONFIRM_PATH = "/v1/payments/confirm";
    /**
     * 멱등키 접두사. 승인은 PG가 발급한 {@code paymentKey}로, 취소는 취소 이력 id로 고정한다.
     *
     * <p>승인 키에 주문번호를 쓰면 안 된다. 취소 후 같은 주문을 다시 결제할 때 같은 키가 되어,
     * PG가 <b>이전 결제의 응답을 재생</b>한다. {@code paymentKey}는 승인 시도마다 다르므로
     * 재시도에는 같고 새 시도에는 다르다는 조건을 정확히 만족한다.
     */
    private static final String APPROVE_KEY_PREFIX = "approve-";
    private static final String CANCEL_KEY_PREFIX = "cancel-";

    private static final String INQUIRY_BY_ORDER_PATH = "/v1/payments/orders/{orderId}";
    /** 승인이 완료된 상태. */
    private static final String APPROVED_STATUS = "DONE";
    /**
     * 아직 결론이 나지 않은 상태. 실패로 확정하면 안 되고 다음 대사 주기에 다시 봐야 한다.
     * {@code READY}는 인증 전, {@code IN_PROGRESS}는 인증만 끝난 상태, 가상계좌는 입금 대기다.
     */
    private static final Set<String> PENDING_STATUSES =
            Set.of("READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT");
    private static final String CANCEL_PATH = "/v1/payments/{paymentKey}/cancel";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * 사용자가 결제 수단을 바꿔 재시도해야 하는 거절 사유. 그 외 실패는 우리 쪽 오류로 다룬다.
     * 목록에 없는 코드를 402로 응답하면 고객에게 "잔액이 부족하다"고 잘못 안내하게 된다.
     */
    private static final Set<String> DECLINE_CODES = Set.of(
            "REJECT_CARD_COMPANY", "EXCEED_MAX_AMOUNT", "EXCEED_MAX_DAILY_PAYMENT_COUNT",
            "EXCEED_MAX_ONE_DAY_AMOUNT", "NOT_ENOUGH_BALANCE", "INVALID_CARD_EXPIRATION",
            "INVALID_STOPPED_CARD", "EXCEED_MAX_AUTH_COUNT", "CARD_LIMIT_EXCEEDED");

    private final RestClient restClient;

    public TossPgClient(TossPaymentProperties properties) {
        this.restClient = OutboundRestClients.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.secretKey()))
                .build();
    }

    @Override
    public Approval approve(String paymentKey, Long orderId, long amount) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("paymentKey", paymentKey);
        request.put("orderId", String.valueOf(orderId));
        request.put("amount", amount);

        Map<String, Object> response = post(CONFIRM_PATH, request, null, "승인", APPROVE_KEY_PREFIX + paymentKey);
        return new Approval(
                String.valueOf(response.get("paymentKey")),
                String.valueOf(response.get("method")),
                receiptUrl(response));
    }

    /**
     * 응답에서 문자열 값을 꺼낸다.
     *
     * <p>{@code String.valueOf}를 그대로 쓰면 값이 없을 때 문자열 {@code "null"}이 만들어진다.
     * 그 값이 PG 식별자 자리에 저장되면 없는 것이 아니라 <b>이상한 값이 있는 것</b>이 되어,
     * 조회도 안 되고 비어 있는지 확인하는 코드에도 걸리지 않는다.
     */
    private static String text(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    @Override
    public Optional<Inquiry> findByOrderId(Long orderId) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(INQUIRY_BY_ORDER_PATH, orderId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
            String status = text(body, "status");
            if (status == null) {
                // 상태를 읽을 수 없는 응답을 "결제 없음"으로 다루면 대사가 실패로 못박는다.
                // 판정 불가는 예외로 알려 선점을 풀고 다음 주기에 다시 묻게 한다.
                throw pgFailed("조회", null);
            }
            return Optional.of(new Inquiry(
                    text(body, "paymentKey"),
                    status,
                    text(body, "method"),
                    receiptUrl(body),
                    APPROVED_STATUS.equals(status),
                    PENDING_STATUSES.contains(status)));
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 승인 요청이 PG에 닿지도 않은 경우다. 결제가 일어나지 않은 것이 확실하다.
                return Optional.empty();
            }
            throw pgFailed("조회", e);
        } catch (Exception e) {
            throw pgFailed("조회", e);
        }
    }

    @Override
    public Cancellation cancel(String paymentKey, long amount, String reason, Long cancelId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("cancelReason", reason);
        request.put("cancelAmount", amount);

        Map<String, Object> response = post(CANCEL_PATH, request, paymentKey, "취소", CANCEL_KEY_PREFIX + cancelId);
        return new Cancellation(text(response, "lastTransactionKey"));
    }

    @SuppressWarnings("unchecked")
    /**
     * @param idempotencyKey <b>재시도해도 같은 값이어야 한다.</b> 호출마다 새로 만들면 PG가 재시도를
     *                       같은 요청으로 알아보지 못해 멱등키를 보내는 의미가 사라진다
     */
    private Map<String, Object> post(String path, Map<String, Object> request,
                                     String paymentKey, String operation, String idempotencyKey) {
        try {
            RestClient.RequestBodySpec spec = paymentKey == null
                    ? restClient.post().uri(path)
                    : restClient.post().uri(path, paymentKey);
            Map<String, Object> body = spec
                    .contentType(MediaType.APPLICATION_JSON)
                    // 재시도가 이중 결제·이중 취소가 되지 않게 한다.
                    .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                    .body(request)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
            if (body == null) {
                throw pgFailed(operation, null);
            }
            return body;
        } catch (RestClientResponseException e) {
            throw translate(e, operation);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw pgFailed(operation, e);
        }
    }

    /**
     * PG 오류를 우리 예외로 옮긴다.
     *
     * <p>거절({@code 402})과 그 밖의 실패를 가르는 것이 핵심이다. 거절은 사용자가 수단을 바꿔
     * 다시 시도할 일이고, 나머지는 우리가 재시도하거나 사람이 볼 일이다.
     */
    private RuntimeException translate(RestClientResponseException e, String operation) {
        String code = errorCode(e);
        log.warn("토스 {} 실패: status={}, code={}", operation, e.getStatusCode(), code);
        if (DECLINE_CODES.contains(code)) {
            return new PaymentDeclinedException();
        }
        return pgFailed(operation, e);
    }

    private String errorCode(RestClientResponseException e) {
        try {
            // 토스 오류 응답은 {"code": "...", "message": "..."} 형태다.
            return String.valueOf(e.getResponseBodyAs(Map.class).get("code"));
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    /** 영수증 주소는 {@code receipt.url}에 담겨 온다. 승인 시점에만 받을 수 있어 저장해 둔다. */
    private String receiptUrl(Map<String, Object> response) {
        if (!(response.get("receipt") instanceof Map<?, ?> receipt)) {
            return null;
        }
        // String.valueOf를 쓰면 url이 없을 때 문자열 "null"이 영수증 주소로 저장돼
        // 고객에게 깨진 링크가 나간다.
        Object url = receipt.get("url");
        return url == null ? null : String.valueOf(url);
    }

    private BusinessException pgFailed(String operation, Exception cause) {
        // PG 응답 원문에는 내부 정보가 담기므로 사용자 응답에 싣지 않는다(시큐어코딩가이드 BE-17).
        log.error("토스 {} 처리 실패", operation, cause);
        return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                "결제 처리 중 오류가 발생했습니다. 다시 시도해주세요.");
    }

    /** 시크릿 키 뒤에 콜론을 붙여 Base64로 인코딩한다. 콜론을 빼면 인증이 실패한다. */
    private static String basicAuth(String secretKey) {
        String encoded = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
