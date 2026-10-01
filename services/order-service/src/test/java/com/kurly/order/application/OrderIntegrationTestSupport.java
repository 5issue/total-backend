package com.kurly.order.application;

import com.kurly.common.security.AuthenticatedPrincipal;
import com.kurly.common.security.Role;
import com.kurly.order.domain.cart.Cart;
import com.kurly.order.domain.cart.CartItem;
import com.kurly.order.domain.cart.CartRepository;
import com.kurly.order.domain.order.Order;
import com.kurly.order.domain.order.OrderRepository;
import com.kurly.order.presentation.dto.CheckoutRequestDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;

import java.util.List;

import static com.kurly.order.application.fixture.OrderIntegrationFixture.address;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.cart;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.item;
import static com.kurly.order.application.fixture.OrderIntegrationFixture.product;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "kurly.security.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false"
})
@Transactional
abstract class OrderIntegrationTestSupport {

    static final long MEMBER_ID = 1L;

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("order_db")
            .withUsername("order")
            .withPassword("order");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired OrderService orderService;
    @Autowired CartRepository cartRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean OrderExternalService externalService;
    @MockitoBean CartExternalService cartExternalService;

    AuthenticatedPrincipal me;

    @BeforeEach
    void setUpPrincipal() {
        me = new AuthenticatedPrincipal(MEMBER_ID, Role.USER);
    }

    Cart savedCart(CartItem... items) {
        Cart saved = cartRepository.save(cart(MEMBER_ID, items));
        entityManager.flush();
        return saved;
    }

    void mockCheckoutExternalServices(Long productId, String productName, Long salePrice) {
        when(externalService.getAddress(MEMBER_ID, 10L)).thenReturn(address());
        when(cartExternalService.getProducts(List.of(productId)))
                .thenReturn(List.of(product(productId, productName, salePrice)));
    }

    Order checkoutAndPlaceOrder() {
        Cart cart = savedCart(item(101L, 2));
        mockCheckoutExternalServices(101L, "샐러드", 8_000L);
        Long orderId = orderService.checkout(
                me, new CheckoutRequestDto(List.of(cart.getItems().getFirst().getId()))).orderId();
        orderService.placeOrder(me, orderId);
        return orderRepository.findById(orderId).orElseThrow();
    }

    void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
