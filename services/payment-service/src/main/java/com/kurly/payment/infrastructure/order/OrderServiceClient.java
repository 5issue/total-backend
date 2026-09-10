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
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * order-service 내부 API 연동.
 *
 * <p>사용자 토큰을 전파한다. 주문 조회는 소유자 확인이 걸린 내부 API라 토큰 없이는 401이 된다
 * (인증인가_설계서 3.4).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "payment.order.client", havingValue = "http", matchIfMissing = true)
public class OrderServiceClient implements OrderClient {

    private static final String FETCH_PATH = "/internal/v1/orders/{orderId}";
    private static final String COMPLETE_PAY_PATH = "/internal/v1/orders/{orderId}/complete-pay";

    /** 이 상태에서만 결제를 받는다. 그 외는 이미 결제됐거나 만료된 주문이다. */
    private static final String PAYABLE_STATUS = "PAYMENT_PENDING";

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

        Map<String, Object> order = unwrap(body);
        if (order == null || order.get("orderId") == null) {
            log.error("주문 응답 형식이 예상과 다름: orderId={}, body={}", orderId, body);
            throw orderLookupFailed(orderId, null);
        }
        return toSnapshot(order);
    }

    @Override
    public void completePayment(Long orderId) {
        try {
            restClient.post().uri(COMPLETE_PAY_PATH, orderId).retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                // 결제 유효시간(5분)이 지나 주문이 만료됐다. 호출부가 보상 취소를 수행한다.
                throw new OrderAlreadyExpiredException(orderId);
            }
            log.error("주문 결제 완료 통보 실패: orderId={}", orderId, e);
            throw new BusinessException(GlobalErrorCode.INTERNAL_SERVER_ERROR,
                    "주문 정보 처리 중 오류가 발생했습니다.");
        }
    }

    private OrderSnapshot toSnapshot(Map<String, Object> order) {
        String status = String.valueOf(order.get("status"));
        return new OrderSnapshot(
                Long.valueOf(order.get("orderId").toString()),
                Long.valueOf(order.get("userId").toString()),
                Long.parseLong(order.get("amount").toString()),
                PAYABLE_STATUS.equals(status));
    }

    /**
     * 명세는 본문을 감싸지 않은 형태로 정의하지만, 팀 컨벤션은 모든 컨트롤러가 {@code ApiResponse}로
     * 감싸는 것이다. 어느 쪽이 와도 읽을 수 있게 한 겹만 벗겨본다.
     *
     * <p>양쪽을 받아주는 것은 임시 조치다. order-service 구현이 확정되면 한쪽으로 고정해야 한다.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        if (body.get("orderId") != null) {
            return body;
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
