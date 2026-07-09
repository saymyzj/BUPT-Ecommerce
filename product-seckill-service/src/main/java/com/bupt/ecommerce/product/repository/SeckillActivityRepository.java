package com.bupt.ecommerce.product.repository;

import com.bupt.ecommerce.product.entity.SeckillActivity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SeckillActivityRepository extends JpaRepository<SeckillActivity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SeckillActivity a where a.id = :activityId")
    Optional<SeckillActivity> findByIdForUpdate(@Param("activityId") Long activityId);
}
