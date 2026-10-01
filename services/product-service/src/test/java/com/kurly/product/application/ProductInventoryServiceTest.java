package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kurly.common.exception.EntityNotFoundException;
import com.kurly.product.domain.dto.ReserveItem;
import com.kurly.product.domain.repository.ProductInventoryRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductInventory;
import com.kurly.product.infrastructure.jpa.ProductConsumedEventJpaRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductInventoryServiceTest {

    @Mock
    private ProductInventoryRepository productInventoryRepository;
    @Mock
    private OutboxService outboxService;
    @Mock
    private ProductConsumedEventJpaRepository productConsumedEventJpaRepository;

    private ProductInventoryService productInventoryService;

    @BeforeEach
    void setUp() {
        productInventoryService = new ProductInventoryService(productInventoryRepository, outboxService,
                productConsumedEventJpaRepository);
    }

    @Test
    @DisplayName("입고 완료 시 base_quantity가 늘고, 품절 상태였던 상품은 판매 상태로 전환된다")
    void increaseStock_soldOutProduct_restocksAndSyncsRedis() {
        Product product = newProduct(ProductStatus.SOLDOUT);
        ProductInventory inventory = ProductInventory.builder()
                .product(product).baseQuantity(10).reservedQuantity(0).build();
        when(productInventoryRepository.findByProductId(5L)).thenReturn(Optional.of(inventory));

        productInventoryService.increaseStock(5L, 7);

        assertThat(inventory.getBaseQuantity()).isEqualTo(17);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE);
        verify(productInventoryRepository).increaseInventory(5L, 7);
    }

    @Test
    @DisplayName("판매 중이던 상품은 입고돼도 상태가 바뀌지 않는다")
    void increaseStock_onSaleProduct_statusUnchanged() {
        Product product = newProduct(ProductStatus.SALE);
        ProductInventory inventory = ProductInventory.builder()
                .product(product).baseQuantity(10).reservedQuantity(0).build();
        when(productInventoryRepository.findByProductId(5L)).thenReturn(Optional.of(inventory));

        productInventoryService.increaseStock(5L, 3);

        assertThat(inventory.getBaseQuantity()).isEqualTo(13);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SALE);
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고를 입고 처리하려 하면 예외를 던진다")
    void increaseStock_inventoryNotFound_throws() {
        when(productInventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productInventoryService.increaseStock(99L, 1))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("출고 완료 시 base_quantity와 reserved_quantity가 함께 줄어 available_quantity는 그대로다")
    void finalizeOutbound_decreasesBaseAndReservedTogether() {
        Product product = newProduct(ProductStatus.SALE);
        ProductInventory inventory = ProductInventory.builder()
                .product(product).baseQuantity(20).reservedQuantity(5).build();
        when(productInventoryRepository.findByProductId(5L)).thenReturn(Optional.of(inventory));

        productInventoryService.finalizeOutbound(List.of(new ReserveItem(5L, 5)));

        assertThat(inventory.getBaseQuantity()).isEqualTo(15);
        assertThat(inventory.getReservedQuantity()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(15);
        verify(productInventoryRepository).finalizeOutboundInventory(5L, 5);
    }

    @Test
    @DisplayName("출고 수량이 reserved_quantity보다 많아도 0 밑으로 내려가지 않는다")
    void finalizeOutbound_neverGoesNegative() {
        Product product = newProduct(ProductStatus.SALE);
        ProductInventory inventory = ProductInventory.builder()
                .product(product).baseQuantity(3).reservedQuantity(3).build();
        when(productInventoryRepository.findByProductId(5L)).thenReturn(Optional.of(inventory));

        productInventoryService.finalizeOutbound(List.of(new ReserveItem(5L, 10)));

        assertThat(inventory.getBaseQuantity()).isZero();
        assertThat(inventory.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 상품 재고를 출고 완료 처리하려 하면 예외를 던진다")
    void finalizeOutbound_inventoryNotFound_throws() {
        when(productInventoryRepository.findByProductId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productInventoryService.finalizeOutbound(List.of(new ReserveItem(99L, 1))))
                .isInstanceOf(EntityNotFoundException.class);
    }

    private static Product newProduct(ProductStatus status) {
        return Product.builder()
                .name("test")
                .price(1_000L)
                .type(ProductType.UNIT)
                .status(status)
                .likeCount(0)
                .totalSalesCount(0L)
                .build();
    }
}
