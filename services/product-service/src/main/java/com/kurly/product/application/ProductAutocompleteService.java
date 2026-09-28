package com.kurly.product.application;

import com.kurly.product.infrastructure.jpa.SearchKeywordJpaRepository;
import java.util.Comparator;
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
 * 자동완성은 상품명 원문이 아니라 {@code search_keyword} 테이블에 큐레이션된 정제 키워드를
 * 대상으로 한다 — "전용목장우유 900mL"/"전용목장우유 1.8L"처럼 용량만 다른 상품이 중복
 * 제안되는 것을 피하고, 브랜드명도 자동완성 대상에 포함하기 위함이다.
 * <p>
 * Redis ZSET({@link #INDEX_KEY})의 사전식 range 조회(ZRANGEBYLEX)로 처리하고, 색인이 아직 못
 * 탄 경우(재구축 주기 사이)는 DB substring 조회로 폴백한다. "전용목장우유"처럼 띄어쓰기 없이
 * 붙은 단어 중간의 "우유"도 찾을 수 있어야 해서, 키워드 전체가 아니라 모든 시작 위치별
 * 접미사(suffix)를 각각 ZSET 멤버로 인덱싱한다({@code lower(keyword.substring(i)) + DELIMITER
 * + keyword}). ZRANGEBYLEX는 prefix 매칭만 지원하므로, 이렇게 해야 "문자열 어디서 시작하든"
 * 매칭이 가능해진다.
 * <p>
 * 응답은 검색 빈도 데이터가 없어 인기순 정렬을 할 수 없으므로, 대신 짧고 일반적인 키워드가
 * 먼저 나오도록 길이 오름차순 → 가나다순으로 정렬한다("우유"가 "우유 식빵"보다 먼저). 정렬 후
 * 상위 {@code size}개만 자르므로, 원본 조회는 {@code size}보다 넉넉히
 * ({@link #CANDIDATE_FETCH_MULTIPLIER}배) 가져와야 정렬 결과가 뒤틀리지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductAutocompleteService {

    private static final String INDEX_KEY = "product:autocomplete:names";
    private static final String STAGING_KEY_PREFIX = "product:autocomplete:names:staging:";
    private static final char DELIMITER = '\u0001';
    private static final String LEX_UPPER_BOUND_SUFFIX = "￿";
    private static final int CANDIDATE_FETCH_MULTIPLIER = 5;
    private static final Comparator<String> BY_LENGTH_THEN_ALPHABETICAL =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    private final SearchKeywordJpaRepository searchKeywordRepository;
    private final StringRedisTemplate redisTemplate;

    public List<String> autocomplete(String keyword, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        List<String> cached = findFromCache(keyword.trim().toLowerCase(Locale.ROOT), size);
        List<String> candidates = cached.isEmpty()
                ? searchKeywordRepository.findByKeywordContaining(
                        keyword.trim(), PageRequest.of(0, size * CANDIDATE_FETCH_MULTIPLIER))
                : cached;

        return candidates.stream()
                .sorted(BY_LENGTH_THEN_ALPHABETICAL)
                .limit(size)
                .toList();
    }

    private List<String> findFromCache(String normalizedPrefix, int size) {
        Range<String> range = Range.rightOpen(normalizedPrefix, normalizedPrefix + LEX_UPPER_BOUND_SUFFIX);
        Set<String> members = redisTemplate.opsForZSet()
                .rangeByLex(INDEX_KEY, range, Limit.limit().count(size * CANDIDATE_FETCH_MULTIPLIER));
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        Set<String> keywords = new LinkedHashSet<>();
        for (String member : members) {
            int delimiterIndex = member.indexOf(DELIMITER);
            keywords.add(delimiterIndex < 0 ? member : member.substring(delimiterIndex + 1));
        }
        return List.copyOf(keywords);
    }

    public void rebuildIndex() {
        List<String> keywords = searchKeywordRepository.findAllKeywords();
        String stagingKey = STAGING_KEY_PREFIX + System.nanoTime();
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        long staged = 0;
        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            for (int start = 0; start < keyword.length(); start++) {
                if (Character.isWhitespace(keyword.charAt(start))) {
                    continue;
                }
                String member = lowerKeyword.substring(start) + DELIMITER + keyword;
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
