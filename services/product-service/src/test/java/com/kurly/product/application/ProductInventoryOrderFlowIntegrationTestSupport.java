package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kurly.product.application.port.EventPublisher;
import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.enums.ConsumedEventType;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.entity.ProductOutbox;
import com.kurly.product.infrastructure.jpa.ProductConsumedEventJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductInventoryJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductOutboxJpaRepository;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 주문 흐름(선점 → 확정 → 해제/복구) 통합 테스트의 공통 준비 코드.
 *
 * <p>정상 흐름은 {@link ProductInventoryOrderFlowIntegrationTest}, 예외 흐름은
 * {@link ProductInventoryOrderFlowIntegrationExceptionTest} 가 이 클래스를 상속해 쓴다(팀 컨벤션: 예외 테스트는 파일 분리).
 *
 * <p>선점 상태는 Redis Lua 스크립트가, 확정 수량은 PostgreSQL 이 관리하므로 목 대신 실제 Redis·PostgreSQL 을
 * 쓴다. 다만 outbox 의 발행 대상인 {@link EventPublisher} 는 목으로 바꿔서, 테스트가 만든 가짜 주문 이벤트가
 * 로컬 RabbitMQ 로 나가 다른 서비스에 닿지 않게 한다(outbox row 적재는 그대로 일어난다).
 *
 * <p>테스트가 만든 상품·재고·outbox·Redis 키만 지운다. 기존 로컬 데이터는 건드리지 않는다.
 * 로컬 Postgres(5436)·Redis(6379)·RabbitMQ(5672)가 떠 있어야 하므로 기본 빌드에서는 건너뛴다. 건너뛰는 조건
 * ({@code @EnabledIfEnvironmentVariable})은 상속되지 않아서 이 클래스가 아니라 각 테스트 클래스에 직접 붙인다.
 * 실행: {@code PRODUCT_INTEGRATION_TEST=true ./gradlew :product-service:test
 * --tests '*ProductInventoryOrderFlowIntegration*'}
 */
@SpringBootTest(properties = {
        "product.outbox.scheduling.enabled=false",
        "kurly.security.enabled=false"
})
abstract class ProductInventoryOrderFlowIntegrationTestSupport {

    protected static final int BASE_QUANTITY = 10;

    @Autowired protected ProductInventoryService productInventoryService;
    @Autowired protected ProductJpaRepository productJpaRepository;
    @Autowired protected ProductInventoryJpaRepository inventoryJpaRepository;
    @Autowired protected ProductOutboxJpaRepository outboxJpaRepository;
    @Autowired protected ProductConsumedEventJpaRepository consumedEventJpaRepository;
    @Autowired protected StringRedisTemplate redisTemplate;
    @Autowired protected ObjectMapper objectMapper;
    @MockitoBean protected EventPublisher eventPublisher;

    private final List<Long> createdProductIds = new ArrayList<>();
    private final List<String> issuedTokens = new ArrayList<>();
    private final List<Long> issuedOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        List<String> redisKeys = new ArrayList<>();
        createdProductIds.forEach(id -> redisKeys.add(inventoryKey(id)));
        issuedTokens.forEach(token -> redisKeys.add(reservationKey(token)));
        if (!redisKeys.isEmpty()) {
            redisTemplate.delete(redisKeys);
        }
        outboxJpaRepository.deleteAll(issuedOrderIds.stream().flatMap(id -> outboxOf(id).stream()).toList());
        deleteIdempotencyMarkers();
        if (!createdProductIds.isEmpty()) {
            inventoryJpaRepository.deleteAll(inventoryJpaRepository.findByProductIdIn(createdProductIds));
            productJpaRepository.deleteAllById(createdProductIds);
        }
        createdProductIds.clear();
        issuedTokens.clear();
        issuedOrderIds.clear();
    }

    /** confirm/restore 가 인박스에 남긴 멱등성 마커 중 이 테스트가 만든 것만 지운다. */
    private void deleteIdempotencyMarkers() {
        Set<String> markerEventIds = Stream.concat(
                        issuedTokens.stream().map(token -> markerEventId(ConsumedEventType.INVENTORY_CONFIRM_REQUESTED, token)),
                        issuedOrderIds.stream().map(id -> markerEventId(ConsumedEventType.INVENTORY_RESTORE_REQUESTED, String.valueOf(id))))
                .collect(Collectors.toSet());
        consumedEventJpaRepository.deleteAll(consumedEventJpaRepository.findAll().stream()
                .filter(event -> markerEventIds.contains(event.getEventId()))
                .toList());
    }

    /** {@code ProductInventoryService} 가 마커를 적재할 때 쓰는 event_id 규칙과 같아야 한다. */
    private static String markerEventId(ConsumedEventType type, String requestKey) {
        return UUID.nameUUIDFromBytes((type + ":" + requestKey).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** 선점하고 확정까지 마친 주문을 만든다. */
    protected void confirmOrder(long orderId, long productId, int quantity) {
        String token = newToken();
        productInventoryService.hold(token, items(productId, quantity));
        productInventoryService.confirm(token, orderId, items(productId, quantity));
    }

    /** 재고 DB 행과 Redis 캐시를 함께 만든다(캐시가 있어야 Lua 스크립트가 바로 동작한다). */
    protected long productWithStock(int baseQuantity) {
        Product product = productJpaRepository.save(Product.builder()
                .name("order-flow-test-" + UUID.randomUUID())
                .price(1_000L)
                .type(ProductType.UNIT)
                .status(ProductStatus.SALE)
                .likeCount(0)
                .totalSalesCount(0L)
                .build());
        inventoryJpaRepository.save(ProductInventory.builder()
                .product(product).baseQuantity(baseQuantity).reservedQuantity(0).build());
        createdProductIds.add(product.getId());
        redisTemplate.opsForHash().putAll(inventoryKey(product.getId()),
                Map.of("base_quantity", String.valueOf(baseQuantity), "reserved_quantity", "0"));
        return product.getId();
    }

    protected String newToken() {
        String token = UUID.randomUUID().toString();
        issuedTokens.add(token);
        return token;
    }

    protected long newOrderId() {
        long orderId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE / 2);
        issuedOrderIds.add(orderId);
        return orderId;
    }

    protected static List<ReserveItem> items(long productId, int quantity) {
        return List.of(new ReserveItem(productId, quantity));
    }

    protected int dbReserved(long productId) {
        return inventoryJpaRepository.findByProductIdIn(List.of(productId)).getFirst().getReservedQuantity();
    }

    protected int redisReserved(long productId) {
        Object value = redisTemplate.opsForHash().get(inventoryKey(productId), "reserved_quantity");
        assertThat(value).as("Redis 재고 캐시가 있어야 한다: productId=%d", productId).isNotNull();
        return Integer.parseInt(value.toString());
    }

    protected String reservationStatus(String token) {
        Object status = redisTemplate.opsForHash().get(reservationKey(token), "status");
        return status == null ? null : status.toString();
    }

    /** 주문에 대해 적재된 outbox 이벤트의 status 를 적재 순서대로 돌려준다. */
    protected List<String> outboxStatuses(long orderId) {
        return outboxOf(orderId).stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(outbox -> payload(outbox).path("status").asString())
                .toList();
    }

    private List<ProductOutbox> outboxOf(long orderId) {
        return outboxJpaRepository.findAll().stream()
                .filter(outbox -> payload(outbox).path("orderId").asLong() == orderId)
                .toList();
    }

    private JsonNode payload(ProductOutbox outbox) {
        return objectMapper.readTree(outbox.getPayload());
    }

    private static String inventoryKey(long productId) {
        return "product:inventory:" + productId;
    }

    private static String reservationKey(String token) {
        return "reservation:" + token;
    }
}
