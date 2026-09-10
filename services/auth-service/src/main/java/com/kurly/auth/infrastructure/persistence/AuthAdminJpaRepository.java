package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthAdminJpaRepository extends JpaRepository<AuthAdmin, Long>, AuthAdminRepository {

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.retryCount = a.retryCount + 1 where a.id = :adminId")
    void increaseRetryCount(@Param("adminId") Long adminId);

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends AuthAdmin> S save(S entity);

    @Override
    Optional<AuthAdmin> findById(Long id);

    /**
     * 잠금과 함께 카운터도 0으로 되돌린다. 잠금 자체가 "허용 횟수를 소진했다"는 사실을 표현하므로
     * 카운터를 남겨둘 이유가 없다. 남겨두면 잠금이 시간으로 풀린 뒤 1회 실패만으로 임계치를 다시
     * 넘겨 즉시 재잠금되어, 최초 잠금 이후 임계값이 사실상 1회로 떨어진다.
     */
    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.lockedUntil = :lockedUntil, a.retryCount = 0 where a.id = :adminId")
    void lockUntil(@Param("adminId") Long adminId, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.retryCount = 0, a.lockedUntil = null where a.id = :adminId")
    void clearLoginFailures(@Param("adminId") Long adminId);
}
