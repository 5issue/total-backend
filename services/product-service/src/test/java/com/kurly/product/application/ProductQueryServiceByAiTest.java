package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kurly.common.exception.BusinessException;
import com.kurly.common.exception.GlobalErrorCode;
import com.kurly.product.application.support.HomeLayoutProvider;
import com.kurly.product.domain.repository.ProductRepository;
import com.kurly.product.infrastructure.entity.Product;
import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.entity.Product.ProductType;
import com.kurly.product.infrastructure.entity.ProductMedia;
import com.kurly.product.infrastructure.entity.ProductMedia.MediaRole;
import com.kurly.product.infrastructure.jpa.ProductMediaJpaRepository;
import com.kurly.product.infrastructure.jpa.ProductSpecJpaRepository;
import com.kurly.product.presentation.dto.ProductsByAiResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProductQueryServiceByAiTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductMediaJpaRepository productMediaRepository;
    @Mock
    private ProductSpecJpaRepository productSpecRepository;
    @Mock
    private HomeLayoutProvider homeLayoutProvider;
    @Mock
    private ProductFilterService productFilterService;

    private ProductQueryService service;

    @BeforeEach
    void setUp() {
        service = new ProductQueryService(productRepository, productMediaRepository, productSpecRepository,
                homeLayoutProvider, productFilterService);
    }

    private Product product(long id, ProductType type, Long parentId, long aiProductId, String sku) {
        Product product = Product.builder()
                .skuCode(sku).parentId(parentId).name("상품" + id).brand("브랜드").price(1000L)
                .discountRate(10).salePrice(900L).type(type).status(ProductStatus.SALE)
                .likeCount(0).totalSalesCount(0L).build();
        ReflectionTestUtils.setField(product, "id", id);
        ReflectionTestUtils.setField(product, "aiProductId", aiProductId);
        return product;
    }

    private ProductMedia thumbnail(Product group, String url) {
        return ProductMedia.builder().product(group).mediaUrl(url).mediaRole(MediaRole.THUMBNAIL).sequence(1).build();
    }

    @Test
    @DisplayName("요청 순서를 지키고 같은 값에서는 GROUP, UNIT 순으로 돌려주며 UNIT 썸네일은 부모 GROUP 것을 쓴다")
    void ordersByRequestAndUsesParentThumbnail() {
        Product group = product(10, ProductType.GROUP, null, 753, "SKU-A0003");
        Product unit = product(11, ProductType.UNIT, 10L, 753, "SKU-A0003-1");
        Product other = product(20, ProductType.UNIT, 30L, 754, "M00000925417");
        Product otherGroup = product(30, ProductType.GROUP, null, 999, "SKU-A0004");
        when(productRepository.findByAiProductIds(List.of(754L, 753L, 1L)))
                .thenReturn(List.of(unit, other, group));
        when(productMediaRepository.findByProductIdInAndMediaRole(anyList(), eq(MediaRole.THUMBNAIL)))
                .thenReturn(List.of(thumbnail(group, "https://img/a.jpg"), thumbnail(otherGroup, "https://img/b.jpg")));

        ProductsByAiResponse response = service.getProductsByAiProductIds(Arrays.asList(754L, 753L, 754L, 1L));

        assertThat(response.items()).extracting("id").containsExactly(20L, 10L, 11L);
        assertThat(response.items()).extracting("thumbnailUrl")
                .containsExactly("https://img/b.jpg", "https://img/a.jpg", "https://img/a.jpg");
        assertThat(response.notFoundAiProductIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("일치하는 상품이 없으면 오류 없이 빈 items 와 notFound 를 반환한다")
    void nothingFound() {
        when(productRepository.findByAiProductIds(List.of(5L))).thenReturn(List.of());

        ProductsByAiResponse response = service.getProductsByAiProductIds(List.of(5L));

        assertThat(response.items()).isEmpty();
        assertThat(response.notFoundAiProductIds()).containsExactly(5L);
        verify(productMediaRepository, never()).findByProductIdInAndMediaRole(anyList(), eq(MediaRole.THUMBNAIL));
    }

    @Test
    @DisplayName("비어 있거나 null 이 섞인 요청은 INVALID_INPUT_VALUE")
    void rejectsEmptyOrNull() {
        assertThatThrownBy(() -> service.getProductsByAiProductIds(List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));
        assertThatThrownBy(() -> service.getProductsByAiProductIds(Arrays.asList(1L, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("중복을 제거하고 100개를 넘으면 INVALID_INPUT_VALUE, 정확히 100개는 허용")
    void limitsToHundredDistinct() {
        List<Long> over = new ArrayList<>(LongStream.rangeClosed(1, 101).boxed().toList());
        assertThatThrownBy(() -> service.getProductsByAiProductIds(over))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE));

        List<Long> hundredWithDuplicates = new ArrayList<>(LongStream.rangeClosed(1, 100).boxed().toList());
        hundredWithDuplicates.add(1L);
        when(productRepository.findByAiProductIds(anyList())).thenReturn(List.of());
        assertThat(service.getProductsByAiProductIds(hundredWithDuplicates).notFoundAiProductIds()).hasSize(100);
    }
}
