package com.kurly.product.application;

import com.kurly.product.infrastructure.entity.Product.ProductStatus;
import com.kurly.product.infrastructure.jpa.ProductJpaRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자동완성은 Redis ZSET({@link #INDEX_KEY})의 사전식 range 조회(ZRANGEBYLEX)로 처리하고,
 * 색인이 아직 못 탄 상품(재구축 주기 사이)은 DB substring 조회로 폴백한다.
 * "전용목장우유"처럼 띄어쓰기 없이 붙은 단어 중간의 "우유"도 찾을 수 있어야 해서, 상품명 전체가
 * 아니라 이름의 모든 시작 위치별 접미사(suffix)를 각각 ZSET 멤버로 인덱싱한다
 * ({@code lower(name.substring(i)) + DELIMITER + name}). ZRANGEBYLEX는 prefix 매칭만 지원하므로,
 * 이렇게 해야 "문자열 어디서 시작하든" 매칭이 가능해진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAutocompleteService {

    private static final String INDEX_KEY = "product:autocomplete:names";
    private static final String STAGING_KEY_PREFIX = "product:autocomplete:names:staging:";
    private static final char DELIMITER = '\u0001';
    private static final String LEX_UPPER_BOUND_SUFFIX = "￿";

    private final ProductJpaRepository productJpaRepository;
    private final StringRedisTemplate redisTemplate;

    public List<String> autocomplete(String keyword, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        List<String> cached = findFromCache(keyword.trim().toLowerCase(Locale.ROOT), size);
        if (!cached.isEmpty()) {
            return cached;
        }
        return productJpaRepository.findNamesByStatusAndKeyword(
                ProductStatus.SALE, keyword.trim(), PageRequest.of(0, size));
    }

    private List<String> findFromCache(String normalizedPrefix, int size) {
        Range<String> range = Range.rightOpen(normalizedPrefix, normalizedPrefix + LEX_UPPER_BOUND_SUFFIX);
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByLex(INDEX_KEY, range, Limit.limit().count(size));
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        Set<String> names = new LinkedHashSet<>();
        for (String member : members) {
            int delimiterIndex = member.indexOf(DELIMITER);
            names.add(delimiterIndex < 0 ? member : member.substring(delimiterIndex + 1));
        }
        return List.copyOf(names);
    }

    public void rebuildIndex() {
        List<String> names = productJpaRepository.findNamesByStatus(ProductStatus.SALE);
        String stagingKey = STAGING_KEY_PREFIX + System.nanoTime();
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        long staged = 0;
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String lowerName = name.toLowerCase(Locale.ROOT);
            for (int start = 0; start < name.length(); start++) {
                if (Character.isWhitespace(name.charAt(start))) {
                    continue;
                }
                String member = lowerName.substring(start) + DELIMITER + name;
                if (Boolean.TRUE.equals(zSetOps.add(stagingKey, member, 0))) {
                    staged++;
                }
            }
        }

        if (staged == 0) {
            redisTemplate.delete(INDEX_KEY);
            return;
        }

        redisTemplate.rename(stagingKey, INDEX_KEY);
    }
}
