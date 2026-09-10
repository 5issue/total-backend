package com.kurly.user.infrastructure.persistence;

import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {

    // Spring Data와 도메인 인터페이스가 각각 선언한 save/findById는 서로를 재정의하지 못해,
    // 이 타입으로 호출하면 "reference is ambiguous" 컴파일 오류가 난다.
    // 여기서 한 번 재선언해 가장 구체적인 선언을 만들어 준다.
    @Override
    <S extends User> S save(S entity);

    @Override
    Optional<User> findById(Long id);

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}
