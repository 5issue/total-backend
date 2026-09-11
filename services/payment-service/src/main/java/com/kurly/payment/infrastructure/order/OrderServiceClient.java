package com.kurly.payment.infrastructure.order;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.payment.application.port.OrderClient;
import com.kurly.payment.exception.OrderNotFoundException;
import com.kurly.payment.infrastructure.client.AuthorizationForwarder;
import com.kurly.payment.infrastructure.client.OutboundRestClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * order-service 내부 API 연동.
 *
 * <p>사용자 토큰을 전파한다. 주문 조회는 소유자 확인이 걸린 내부 API라 토큰 없이는 401이 된다
 * (인증인가_설계서 3.4).
 *
 * <p>응답은 팀 컨벤션대로 {@code ApiResponse}로 감싸여 오며 실제 값은 {@code data}에 있다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.order.client", havingValue = "http", matchIfMissing = true)
public class OrderServiceClient implements OrderClient {

    private static final String FETCH_PATH = "/internal/v1/orders/{orderId}";
    private static final String COMPLETE_PAY_PATH = "/internal/v1/orders/{orderId}/complete-pay";

    /**
     * 이 상태에서만 결제를 받는다. 주문 담당자가 확인한 {@code OrderStatus} enum 기준이다.
     * 이 값이 어긋나면 모든 주문이 결제 불가로 판정되어 결제가 전부 막힌다.
     */
    private static final String PAYABLE_STATUS = "PENDING_PAYMENT";
    /** 주문이 결제로 확정된 상태. 이미 이 상태면 우리 통보가 (다른 경로로든) 반영된 것이다. */
    private static final String PAID_STATUS = "PAID";

    private final RestClient restClient;

    public OrderServiceClient(@Value("${order-service.base-url}") String baseUrl) {
        this.restClient = OutboundRestClients.builder()
                .baseUrl(baseUrl)
                .requestInterceptor(new AuthorizationForwarder())
                .build();
    }

    @Override
    public OrderSnapshot fetch(Long orderId) {
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(FETCH_PATH, orderId)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {
                    });
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                // 명세상 404는 "없거나 만료된 주문"이다. 둘을 구분해 노출하지 않는다.
                throw new OrderNotFoundException();
            }
            throw orderLookupFailed(orderId, e);
        } catch (Exception e) {
            throw orderLookupFailed(orderId, e);
        }

        Map<String, Object> order = data(body);
        if (order == null || order.get("orderId") == null) {
            log.error("주문 응답 형식이 예상과 다름: orderId={}, body={}", orderId, body);
            throw orderLookupFailed(orderId, null);
        }
        return toSnapshot(order);
    }

    @Override
    public void completePayment(Long orderId, Long paymentId, long paymentAmount, LocalDateTime paidAt) {
        // LinkedHashMap을 쓰는 이유는 로그에 찍히는 필드 순서를 명세와 맞추기 위함이다.
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("paymentId", paymentId);
        request.put("paymentAmount", paymentAmount);
        request.put("paidAt", paidAt.toString());

        try {
            restClient.post()
                    .uri(COMPLETE_PAY_PATH, orderId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 결제 유효시간(5분)이 지나 주문이 만료됐다. 호출부가 보상 취소를 수행한다.
                throw new OrderAlreadyExpiredException(orderId);
            }
            log.error("주문 결제 완료 통보 실패: orderId={}, status={}", orderId, e.getStatusCode(), e);
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                    "주문 정보 처리 중 오류가 발생했습니다.");
        }
    }

    private OrderSnapshot toSnapshot(Map<String, Object> order) {
        return new OrderSnapshot(
                Long.valueOf(order.get("orderId").toString()),
                Long.valueOf(order.get("userId").toString()),
                Long.parseLong(order.get("amount").toString()),
                PAYABLE_STATUS.equals(String.valueOf(order.get("status"))),
                PAID_STATUS.equals(String.valueOf(order.get("status"))));
    }

    /** 응답은 {@code ApiResponse}로 감싸여 온다. 실제 값은 {@code data}에 있다. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        return body.get("data") instanceof Map<?, ?> data ? (Map<String, Object>) data : null;
    }

    private BusinessException orderLookupFailed(Long orderId, Exception cause) {
        // 주문 금액·소유자를 확인하지 못하면 위변조 검증을 할 수 없으므로 결제를 진행해서는 안 된다.
        log.error("주문 조회 실패: orderId={}", orderId, cause);
        return new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                "주문 정보를 불러오는 중 오류가 발생했습니다.");
    }
}
