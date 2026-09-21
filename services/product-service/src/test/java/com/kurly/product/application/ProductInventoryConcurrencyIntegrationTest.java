package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.exception.ProductErrorCode;
import com.kurly.product.domain.exception.ProductException;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.jpa.ProductInventoryJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductJpaRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 재고 선점(hold)·선점 취소(release)의 동시성 정합성과 처리 시간을 함께 본다.
 *
 * <p>컨트롤러({@code ProductInventoryController})가 그대로 위임하는 {@link ProductInventoryService}를 호출한다.
 * 선점 상태는 Redis Lua 스크립트가 원자적으로 관리하므로, 목이 아니라 실제 Redis·PostgreSQL을 쓴다.
 * 목으로 대신하면 스크립트가 틀려도 초록불이 켜진다.
 *
 * <p><b>테스트에 {@code @Transactional}을 걸지 않는다.</b> 서비스가 스스로 트랜잭션을 열고 닫는 동작
 * (DB 커넥션 획득 포함)까지 처리 시간에 반영되어야 한다.
 *
 * <p>모든 스레드는 {@code startLatch}에서 대기했다가 동시에 출발한다. 스레드 풀 크기는 반드시 스레드 수와
 * 같아야 한다. 풀이 더 작으면 대기 중인 스레드가 풀을 다 차지해 나머지 태스크가 시작하지 못하고
 * {@code readyLatch}가 영원히 풀리지 않는다.
 *
 * <p>처리 시간은 단정하지 않고 {@code [PERF]} 접두어로 출력만 한다(머신·부하에 따라 흔들려 CI에서 깨지기 쉽다).
 * 로컬 Postgres(5436)·Redis(6379)·RabbitMQ(5672)가 떠 있어야 하므로 기본 빌드에서는 건너뛴다.
 * 실행: {@code PRODUCT_INTEGRATION_TEST=true ./gradlew :product-service:test
 * --tests '*ProductInventoryConcurrencyIntegrationTest' -i | grep PERF}
 *
 * <p>테스트가 만든 상품·재고·Redis 키만 지운다. 기존 로컬 데이터는 건드리지 않는다.
 */
@SpringBootTest(properties = {
        "product.outbox.scheduling.enabled=false",
        "kurly.security.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "PRODUCT_INTEGRATION_TEST", matches = "true")
class ProductInventoryConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 100;

    @Autowired ProductInventoryService productInventoryService;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductInventoryJpaRepository inventoryJpaRepository;
    @Autowired StringRedisTemplate redisTemplate;

    private final List<Long> createdProductIds = Collections.synchronizedList(new ArrayList<>());
    private final Queue<String> issuedTokens = new ConcurrentLinkedQueue<>();

    @BeforeAll
    static void warmUp(@Autowired ProductInventoryService service,
                       @Autowired ProductJpaRepository productRepository,
                       @Autowired ProductInventoryJpaRepository inventoryRepository,
                       @Autowired StringRedisTemplate redis) {
        // JIT·Lua 스크립트 SHA 캐시·Hikari 커넥션이 준비되지 않은 첫 호출이 통계를 왜곡하지 않게 미리 데운다.
        Product product = productRepository.save(newProduct());
        inventoryRepository.save(ProductInventory.builder()
                .product(product).baseQuantity(1000).reservedQuantity(0).build());
        redis.opsForHash().putAll(inventoryKey(product.getId()),
                Map.of("base_quantity", "1000", "reserved_quantity", "0"));
        List<String> warmKeys = new ArrayList<>();
        try {
            for (int i = 0; i < 200; i++) {
                String warmToken = UUID.randomUUID().toString();
                warmKeys.add(reservationKey(warmToken));
                service.hold(warmToken, List.of(new ReserveItem(product.getId(), 1)));
                service.release(warmToken);
            }
        } finally {
            redis.delete(warmKeys);
            redis.delete(inventoryKey(product.getId()));
            inventoryRepository.deleteAll(inventoryRepository.findByProductIdIn(List.of(product.getId())));
            productRepository.deleteById(product.getId());
        }
    }

    @AfterEach
    void cleanUp() {
        List<String> keys = new ArrayList<>();
        createdProductIds.forEach(id -> keys.add(inventoryKey(id)));
        issuedTokens.forEach(token -> keys.add(reservationKey(token)));
        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        if (!createdProductIds.isEmpty()) {
            inventoryJpaRepository.deleteAll(inventoryJpaRepository.findByProductIdIn(createdProductIds));
            productJpaRepository.deleteAllById(createdProductIds);
        }
        createdProductIds.clear();
        issuedTokens.clear();
    }

    @Test
    @DisplayName("재고가 충분하면 동시 선점 100건이 모두 성공하고 예약 수량이 정확히 누적된다")
    void hold_sufficientStock_allSucceed() {
        int quantity = 2;
        long productId = createProduct(THREAD_COUNT * quantity, true);

        Result result = runConcurrently(THREAD_COUNT, i -> hold(newToken(), productId, quantity));
        report("hold / 재고 충분", result);

        assertThat(result.failures()).as(result.describeFailures()).isEmpty();
        assertThat(reservedQuantity(productId)).isEqualTo(THREAD_COUNT * quantity);
    }

    @Test
    @DisplayName("재고보다 많이 몰려도 재고 수만큼만 성공하고 초과 판매하지 않는다")
    void hold_insufficientStock_neverOversells() {
        int stock = 30;
        long productId = createProduct(stock, true);

        Result result = runConcurrently(THREAD_COUNT, i -> hold(newToken(), productId, 1));
        report("hold / 재고 30 · 요청 100", result);

        assertThat(result.successCount()).isEqualTo(stock);
        assertThat(result.countFailures(ProductErrorCode.OUT_OF_STOCK)).isEqualTo(THREAD_COUNT - stock);
        assertThat(result.failures()).as(result.describeFailures()).hasSize(THREAD_COUNT - stock);
        assertThat(reservedQuantity(productId)).isEqualTo(stock);
        assertThat(heldTokenCount()).isEqualTo(stock);
    }

    @Test
    @DisplayName("같은 토큰으로 동시에 선점해도 한 번만 반영된다(멱등)")
    void hold_sameTokenConcurrently_countedOnce() {
        long productId = createProduct(THREAD_COUNT * 2, true);
        String token = newToken();

        Result result = runConcurrently(THREAD_COUNT, i -> hold(token, productId, 3));
        report("hold / 같은 토큰 100회", result);

        assertThat(result.failures()).as(result.describeFailures()).isEmpty();
        assertThat(reservedQuantity(productId)).isEqualTo(3);
    }

    @Test
    @DisplayName("선점한 100건을 동시에 취소하면 예약 수량이 0으로 돌아오고 모두 RELEASE 상태가 된다")
    void release_allHeld_restoresStock() {
        long productId = createProduct(THREAD_COUNT, true);
        List<String> tokens = holdSequentially(productId, THREAD_COUNT, 1);
        assertThat(reservedQuantity(productId)).isEqualTo(THREAD_COUNT);

        Result result = runConcurrently(THREAD_COUNT, i -> productInventoryService.release(tokens.get(i)));
        report("release / 서로 다른 토큰 100건", result);

        assertThat(result.failures()).as(result.describeFailures()).isEmpty();
        assertThat(reservedQuantity(productId)).isZero();
        assertThat(tokens).allSatisfy(token -> assertThat(reservationStatus(token)).isEqualTo("RELEASE"));
    }

    @Test
    @DisplayName("같은 토큰을 동시에 여러 번 취소해도 재고는 한 번만 복구된다(멱등)")
    void release_sameTokenConcurrently_restoredOnce() {
        long productId = createProduct(THREAD_COUNT, true);
        // 다른 예약 9건이 남아 있어야, 중복 복구가 일어났을 때 수량이 9 아래로 내려가 드러난다(0 아래는 Lua가 잘라낸다).
        List<String> tokens = holdSequentially(productId, 10, 1);
        assertThat(reservedQuantity(productId)).isEqualTo(10);

        Result result = runConcurrently(THREAD_COUNT, i -> productInventoryService.release(tokens.get(0)));
        report("release / 같은 토큰 100회", result);

        assertThat(result.failures()).as(result.describeFailures()).isEmpty();
        assertThat(reservedQuantity(productId)).isEqualTo(9);
    }

    @Test
    @DisplayName("선점과 선점 취소가 동시에 섞여도 예약 수량이 어긋나지 않는다")
    void holdAndRelease_mixed_keepsQuantityConsistent() {
        int half = THREAD_COUNT / 2;
        long productId = createProduct(THREAD_COUNT, true);
        List<String> preHeld = holdSequentially(productId, half, 1);

        Result result = runConcurrently(THREAD_COUNT, i -> {
            if (i < half) {
                productInventoryService.release(preHeld.get(i));
            } else {
                hold(newToken(), productId, 1);
            }
        });
        report("hold 50 + release 50 혼합", result);

        assertThat(result.failures()).as(result.describeFailures()).isEmpty();
        assertThat(reservedQuantity(productId)).isEqualTo(half);
    }

    @Test
    @DisplayName("Redis 캐시가 비어 있는 상태(콜드 스타트)에서 동시 선점해도 초과 판매하지 않는다")
    void hold_coldCache_neverOversells() {
        int stock = 50;
        long productId = createProduct(stock, false);
        assertThat(redisTemplate.hasKey(inventoryKey(productId))).isFalse();

        Result result = runConcurrently(THREAD_COUNT, i -> hold(newToken(), productId, 1));
        report("hold / 캐시 콜드 · 재고 50 · 요청 100", result);

        assertThat(result.successCount())
                .as("성공 건수 (Redis reserved_quantity=%d, 실패=%s)", reservedQuantity(productId), result.describeFailures())
                .isEqualTo(stock);
        assertThat(reservedQuantity(productId)).isEqualTo(stock);
    }

    // ---- 실행 도우미 ----

    /** 모든 스레드가 준비될 때까지 기다렸다가 동시에 출발시키고, 호출별 소요 시간과 실패를 모은다. */
    private Result runConcurrently(int threadCount, IntConsumer task) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Long> elapsedNanos = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());

        try {
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        long start = System.nanoTime();
                        try {
                            task.accept(index);
                        } catch (Throwable e) {
                            failures.add(e);
                        } finally {
                            elapsedNanos.add(System.nanoTime() - start);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            assertThat(readyLatch.await(10, TimeUnit.SECONDS)).as("모든 스레드가 출발선에 서야 한다").isTrue();
            long wallStart = System.nanoTime();
            startLatch.countDown();
            assertThat(doneLatch.await(60, TimeUnit.SECONDS)).as("60초 안에 전부 끝나야 한다").isTrue();
            long wallNanos = System.nanoTime() - wallStart;

            return new Result(List.copyOf(elapsedNanos), List.copyOf(failures), wallNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    private void report(String label, Result result) {
        List<Long> sorted = result.elapsedNanos().stream().sorted().toList();
        double avg = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        System.out.printf(
                "[PERF] %-32s 스레드=%d 성공=%d 실패=%d | 평균=%.2fms p50=%.2fms p95=%.2fms p99=%.2fms 최대=%.2fms | 전체=%.1fms (%.0f req/s)%n",
                label, sorted.size(), result.successCount(), result.failures().size(),
                avg, percentile(sorted, 50), percentile(sorted, 95), percentile(sorted, 99),
                sorted.getLast() / 1_000_000.0,
                result.wallNanos() / 1_000_000.0,
                sorted.size() / (result.wallNanos() / 1_000_000_000.0));
        if (!result.failures().isEmpty()) {
            System.out.println("[PERF]   실패 내역: " + result.describeFailures());
        }
    }

    /** nearest-rank 방식 백분위수(ms). */
    private double percentile(List<Long> sortedNanos, int percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sortedNanos.size());
        return sortedNanos.get(Math.max(rank, 1) - 1) / 1_000_000.0;
    }

    // ---- 데이터 도우미 ----

    private long createProduct(int baseQuantity, boolean warmCache) {
        Product product = productJpaRepository.save(newProduct());
        inventoryJpaRepository.save(ProductInventory.builder()
                .product(product).baseQuantity(baseQuantity).reservedQuantity(0).build());
        createdProductIds.add(product.getId());
        if (warmCache) {
            redisTemplate.opsForHash().putAll(inventoryKey(product.getId()),
                    Map.of("base_quantity", String.valueOf(baseQuantity), "reserved_quantity", "0"));
        }
        return product.getId();
    }

    private static Product newProduct() {
        return Product.builder()
                .name("concurrency-test-" + UUID.randomUUID())
                .price(1_000L)
                .type(ProductType.UNIT)
                .status(ProductStatus.SALE)
                .likeCount(0)
                .totalSalesCount(0L)
                .build();
    }

    private String newToken() {
        String token = UUID.randomUUID().toString();
        issuedTokens.add(token);
        return token;
    }

    private void hold(String token, long productId, int quantity) {
        productInventoryService.hold(token, List.of(new ReserveItem(productId, quantity)));
    }

    /** 측정 대상이 아닌 사전 준비용 선점. 순차로 실행해 결과가 결정적이다. */
    private List<String> holdSequentially(long productId, int count, int quantity) {
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String token = newToken();
            hold(token, productId, quantity);
            tokens.add(token);
        }
        return tokens;
    }

    private int reservedQuantity(long productId) {
        Object value = redisTemplate.opsForHash().get(inventoryKey(productId), "reserved_quantity");
        assertThat(value).as("Redis 재고 캐시가 있어야 한다: productId=%d", productId).isNotNull();
        return Integer.parseInt(value.toString());
    }

    private String reservationStatus(String token) {
        Object status = redisTemplate.opsForHash().get(reservationKey(token), "status");
        return status == null ? null : status.toString();
    }

    private long heldTokenCount() {
        return issuedTokens.stream().filter(token -> "HOLD".equals(reservationStatus(token))).count();
    }

    private static String inventoryKey(long productId) {
        return "product:inventory:" + productId;
    }

    private static String reservationKey(String token) {
        return "reservation:" + token;
    }

    private record Result(List<Long> elapsedNanos, List<Throwable> failures, long wallNanos) {

        long successCount() {
            return elapsedNanos.size() - failures.size();
        }

        long countFailures(ProductErrorCode errorCode) {
            return failures.stream()
                    .filter(e -> e instanceof ProductException pe && pe.getErrorCode() == errorCode)
                    .count();
        }

        /** 예외 종류별 건수. 실패 원인을 한눈에 보기 위한 요약. */
        String describeFailures() {
            Map<String, Integer> counts = new TreeMap<>();
            for (Throwable failure : failures) {
                String key = failure instanceof ProductException pe
                        ? "ProductException(" + pe.getErrorCode() + ")"
                        : failure.getClass().getSimpleName() + ": " + failure.getMessage();
                counts.merge(key, 1, Integer::sum);
            }
            return counts.isEmpty() ? "실패 없음" : counts.toString();
        }
    }
}
