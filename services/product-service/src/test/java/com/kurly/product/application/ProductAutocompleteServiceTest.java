package com.kurly.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kurly.product.infrastructure.jpa.SearchKeywordJpaRepository;
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
    private SearchKeywordJpaRepository searchKeywordRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ProductAutocompleteService productAutocompleteService;

    @BeforeEach
    void setUp() {
        productAutocompleteService = new ProductAutocompleteService(searchKeywordRepository, redisTemplate);
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
                .thenReturn(Set.of("우유\u0001우유", "우유 저지방\u0001우유 저지방"));

        List<String> result = productAutocompleteService.autocomplete("우유", 10);

        assertThat(result).containsExactly("우유", "우유 저지방");
        verify(searchKeywordRepository, never()).findByKeywordContaining(anyString(), any());
    }

    @Test
    @DisplayName("결과는 짧은 키워드가 먼저 나오도록 길이 오름차순, 길이가 같으면 가나다순으로 정렬된다")
    void resultsAreSortedByLengthThenAlphabetically() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByLex(eq("product:autocomplete:names"), any(Range.class), any(Limit.class)))
                .thenReturn(Set.of(
                        "우유 식빵\u0001우유 식빵",
                        "우유 A2\u0001우유 A2",
                        "우유\u0001우유",
                        "우럭\u0001우럭"));

        List<String> result = productAutocompleteService.autocomplete("우", 10);

        assertThat(result).containsExactly("우럭", "우유", "우유 A2", "우유 식빵");
    }

    @Test
    @DisplayName("Redis에 캐시가 비어 있으면 DB substring 조회로 폴백한다")
    void cacheMissFallsBackToDatabase() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByLex(anyString(), any(Range.class), any(Limit.class)))
                .thenReturn(Set.of());
        when(searchKeywordRepository.findByKeywordContaining(eq("신상"), any()))
                .thenReturn(List.of("신상품A"));

        List<String> result = productAutocompleteService.autocomplete("신상", 10);

        assertThat(result).containsExactly("신상품A");
    }

    @Test
    @DisplayName("색인 재구축 시 정제 키워드의 모든 접미사를 채워서 중간에 붙은 단어도 substring으로 찾을 수 있다")
    void rebuildIndexStagesEverySuffixThenRenames() {
        when(searchKeywordRepository.findAllKeywords())
                .thenReturn(Arrays.asList("바나나", " ", "우유 A2"));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.add(anyString(), anyString(), eq(0.0))).thenReturn(true);

        productAutocompleteService.rebuildIndex();

        verify(zSetOperations).add(anyString(), eq("바나나\u0001바나나"), eq(0.0));
        verify(zSetOperations).add(anyString(), eq("나나\u0001바나나"), eq(0.0));
        verify(zSetOperations).add(anyString(), eq("나\u0001바나나"), eq(0.0));
        // "우유 A2" 중간에 있는 "A2"도 시작 위치가 별도로 색인된다
        verify(zSetOperations).add(anyString(), eq("a2\u0001우유 A2"), eq(0.0));
        // 공백으로 시작하는 접미사는 색인하지 않는다
        verify(zSetOperations, never()).add(anyString(), eq(" a2\u0001우유 A2"), eq(0.0));
        verify(redisTemplate).rename(anyString(), eq("product:autocomplete:names"));
        verify(redisTemplate, never()).delete("product:autocomplete:names");
    }

    @Test
    @DisplayName("정제 키워드가 없으면 staging 없이 기존 색인을 비운다")
    void rebuildIndexClearsWhenNoKeywords() {
        when(searchKeywordRepository.findAllKeywords()).thenReturn(List.of());

        productAutocompleteService.rebuildIndex();

        verify(redisTemplate).delete("product:autocomplete:names");
        verify(redisTemplate, never()).rename(anyString(), anyString());
    }
}
