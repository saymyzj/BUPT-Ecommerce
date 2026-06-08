package com.bupt.ecommerce.user.repository;

import com.bupt.ecommerce.user.entity.UserAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, Long> {

    Optional<UserAccountEntity> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);
}
