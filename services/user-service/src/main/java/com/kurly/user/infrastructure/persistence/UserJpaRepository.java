package com.kurly.user.infrastructure.persistence;

import com.kurly.user.domain.entity.User;
import com.kurly.user.domain.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {
}
