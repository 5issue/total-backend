package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.jpa.ProductJpaRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
class ProductAutocompleteServiceTest {

    @Mock
    private ProductJpaRepository productJpaRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ProductAutocompleteService productAutocompleteService;

    @BeforeEach
    void setUp() {
        productAutocompleteService = new ProductAutocompleteService(productJpaRepository, redisTemplate);
    }

    @Test
    @DisplayName("키워드가 없으면 조회 없이 빈 결과를 반환한다")
    void blankKeywordReturnsEmpty() {
        List<String> result = productAutocompleteService.autocomplete("  ", 10);

        assertThat(result).isEmpty();
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    @DisplayName("Redis ZSET에 캐시가 있으면 DB를 조회하지 않고 원래 표기로 복원해 반환한다")
    void cacheHitReturnsOriginalCasingWithoutDbFallback() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByLex(eq("product:autocomplete:names"), any(Range.class), any(Limit.class)))
                .thenReturn(Set.of("제주감귤\u0001제주감귤", "제주삼겹살\u0001제주삼겹살"));

        List<String> result = productAutocompleteService.autocomplete("제주", 10);

        assertThat(result).containsExactlyInAnyOrder("제주감귤", "제주삼겹살");
        verify(productJpaRepository, never()).findNamesByStatusAndKeyword(any(), anyString(), any());
    }

    @Test
    @DisplayName("Redis에 캐시가 비어 있으면 DB substring 조회로 폴백한다")
    void cacheMissFallsBackToDatabase() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByLex(anyString(), any(Range.class), any(Limit.class)))
                .thenReturn(Set.of());
        when(productJpaRepository.findNamesByStatusAndKeyword(eq(ProductStatus.SALE), eq("신상"), any()))
                .thenReturn(List.of("신상품A"));

        List<String> result = productAutocompleteService.autocomplete("신상", 10);

        assertThat(result).containsExactly("신상품A");
    }

    @Test
    @DisplayName("색인 재구축 시 이름의 모든 접미사를 채워서 중간에 붙은 단어도 substring으로 찾을 수 있다")
    void rebuildIndexStagesEverySuffixThenRenames() {
        when(productJpaRepository.findNamesByStatus(ProductStatus.SALE))
                .thenReturn(Arrays.asList("바나나", " ", null, "청정 우유"));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), eq(0.0))).thenReturn(true);

        productAutocompleteService.rebuildIndex();

        verify(zSetOperations).add(anyString(), eq("바나나\u0001바나나"), eq(0.0));
        verify(zSetOperations).add(anyString(), eq("나나\u0001바나나"), eq(0.0));
        verify(zSetOperations).add(anyString(), eq("나\u0001바나나"), eq(0.0));
        // "청정 우유" 중간에 띄어쓰기 없이 이어지지 않아도, "우유"가 시작하는 위치가 별도로 색인된다
        verify(zSetOperations).add(anyString(), eq("우유\u0001청정 우유"), eq(0.0));
        // 공백으로 시작하는 접미사는 색인하지 않는다
        verify(zSetOperations, never()).add(anyString(), eq(" 우유\u0001청정 우유"), eq(0.0));
        verify(redisTemplate).rename(anyString(), eq("product:autocomplete:names"));
        verify(redisTemplate, never()).delete("product:autocomplete:names");
    }

    @Test
    @DisplayName("판매중 상품이 없으면 staging 없이 기존 색인을 비운다")
    void rebuildIndexClearsWhenNoSalableProducts() {
        when(productJpaRepository.findNamesByStatus(ProductStatus.SALE)).thenReturn(List.of());

        productAutocompleteService.rebuildIndex();

        verify(redisTemplate).delete("product:autocomplete:names");
        verify(redisTemplate, never()).rename(anyString(), anyString());
    }
}
