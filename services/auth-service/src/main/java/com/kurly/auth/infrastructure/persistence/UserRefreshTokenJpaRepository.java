package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.UserRefreshToken;
import com.kurly.auth.domain.repository.UserRefreshTokenRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRefreshTokenJpaRepository
        extends JpaRepository<UserRefreshToken, Long>, UserRefreshTokenRepository {

    /**
     * {@code clearAutomatically}를 켜지 않는다. 영속성 컨텍스트를 비우면 호출부가 들고 있던
     * {@code AuthUser}가 준영속이 되어 새 토큰을 저장할 때 문제가 된다. 이 트랜잭션에서
     * 폐기한 엔티티를 다시 읽지 않으므로 비울 필요도 없다.
     */
    @Override
    @Modifying
    @Query("update UserRefreshToken t set t.revoked = true where t.token = :token and t.revoked = false")
    int revokeIfActive(@Param("token") String token);
}
