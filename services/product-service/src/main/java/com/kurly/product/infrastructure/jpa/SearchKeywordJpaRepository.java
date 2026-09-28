package com.kurly.product.infrastructure.jpa;

import com.kurly.product.infrastructure.entity.SearchKeyword;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchKeywordJpaRepository extends JpaRepository<SearchKeyword, Long> {

    @Query("select k.keyword from SearchKeyword k")
    List<String> findAllKeywords();

    /**
     * 자동완성 Redis 캐시 미스 시 DB 폴백용. ZSET의 suffix 색인과 동일하게 키워드 어디에
     * 있든(substring) 매칭한다.
     */
    @Query("""
            select k.keyword from SearchKeyword k
            where k.keyword ilike concat('%', cast(:keyword as string), '%')
            """)
    List<String> findByKeywordContaining(@Param("keyword") String keyword, Pageable pageable);
}
