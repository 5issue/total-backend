package com.kurly.order.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kurly.order.application.FridgeClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.List;

/**
 * AI 서비스의 냉장고 적재 API 호출.
 *
 * <p>AI 쪽은 관리자 JWT({@code role == "ADMIN"})를 요구한다. 배송 완료 처리는 관리자가 하므로
 * 요청 스코프에 관리자 토큰이 있고, 그것을 그대로 전파한다(인증인가_설계서 3.4).
 *
 * <p><b>재시도하지 않는다.</b> 수량을 누적하는 비멱등 요청이라 응답이 끊겨도 이미 반영돼 있을 수
 * 있다. 타임아웃·5xx를 재시도하면 두 배로 쌓인다(AI팀 합의). 멱등 키가 생기면 그때 켠다.
 */
@Slf4j
@Component
public class AiFridgeClient implements FridgeClient {

    private final RestClient restClient;
    private final String itemsPath;
    /** 주소가 주입되지 않은 상태. 호출하면 실패시키되 기동은 막지 않는다. */
    private final boolean disabled;

    public AiFridgeClient(
            @Value("${services.ai.base-url:}") String baseUrl,
            @Value("${services.ai.fridge-items-path}") String itemsPath,
            @Value("${services.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.ai.read-timeout:5s}") Duration readTimeout) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        this.itemsPath = itemsPath;
        this.disabled = !StringUtils.hasText(baseUrl);
        if (disabled) {
            // 기동은 시키되 조용히 넘어가지는 않는다. 로그를 안 남기면 아무도 모른 채
            // 냉장고만 계속 비어 있게 된다.
            log.warn("AI_BASE_URL이 주입되지 않았습니다. 배송 완료는 정상 처리되지만 "
                    + "냉장고 적재는 건너뜁니다(fridgeSynced=false).");
        }
        this.restClient = RestClient.builder()
                .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "http://ai-service.invalid")
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, currentAuthorization());
                    return execution.execute(request, body);
                })
                .build();
    }

    @Override
    public void addItems(Long userId, List<FridgeItem> items) {
        if (disabled) {
            throw new IllegalStateException(
                    "AI_BASE_URL이 주입되지 않아 냉장고 적재를 건너뜁니다.");
        }
        if (items.isEmpty()) {
            // 넣을 것이 없으면 호출하지 않는다. 빈 요청으로 상대 로그를 더럽힐 이유가 없다.
            return;
        }
        restClient.post()
                .uri(itemsPath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(FridgeItemsRequest.of(userId, items))
                .retrieve()
                .toBodilessEntity();

        log.info("냉장고 적재 완료: userId={}, 품목={}건", userId, items.size());
    }

    /**
     * 요청 스코프의 Authorization 헤더를 꺼낸다.
     *
     * <p><b>없으면 토큰을 지어내지 않고 실패시킨다.</b> 사용자 없는 흐름(배치·메시지 소비)에서
     * 불렸다는 뜻이고, 그런 호출이 관리자 권한으로 나가면 안 된다.
     */
    private String currentAuthorization() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException(
                    "요청 스코프 밖에서 냉장고 적재를 호출했습니다. 전파할 관리자 토큰이 없습니다.");
        }
        HttpServletRequest request = attributes.getRequest();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            throw new IllegalStateException("Authorization 헤더가 없어 냉장고 적재를 호출할 수 없습니다.");
        }
        return header;
    }

    /**
     * AI 명세의 요청 형식. <b>필드 이름이 snake_case다.</b> 이 서비스의 기본은 camelCase라
     * 명시적으로 지정한다.
     *
     * <p>{@code unit}과 {@code expires_at}은 보내지 않는다. 주문·상품 도메인에 없는 값이라
     * 지어낼 수 없고, AI 쪽이 각각 기본값 "개"와 null로 처리하기로 했다.
     */
    private record FridgeItemsRequest(
            @JsonProperty("user_id") Long userId,
            @JsonProperty("items") List<Item> items) {

        static FridgeItemsRequest of(Long userId, List<FridgeItem> items) {
            return new FridgeItemsRequest(userId,
                    items.stream().map(i -> new Item(i.productId(), i.quantity())).toList());
        }

        private record Item(
                @JsonProperty("product_id") Long productId,
                @JsonProperty("quantity") int quantity) {
        }
    }
}
