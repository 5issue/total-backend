package com.kurly.auth.infrastructure.persistence;

import com.kurly.auth.domain.entity.AuthAdmin;
import com.kurly.auth.domain.repository.AuthAdminRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface AuthAdminJpaRepository extends JpaRepository<AuthAdmin, Long>, AuthAdminRepository {

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.retryCount = a.retryCount + 1 where a.id = :adminId")
    void increaseRetryCount(@Param("adminId") Long adminId);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.lockedUntil = :lockedUntil where a.id = :adminId")
    void lockUntil(@Param("adminId") Long adminId, @Param("lockedUntil") LocalDateTime lockedUntil);

    @Override
    @Modifying(clearAutomatically = true)
    @Query("update AuthAdmin a set a.retryCount = 0, a.lockedUntil = null where a.id = :adminId")
    void clearLoginFailures(@Param("adminId") Long adminId);
}
