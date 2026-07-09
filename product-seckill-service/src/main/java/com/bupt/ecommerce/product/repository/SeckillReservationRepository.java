package com.bupt.ecommerce.product.repository;

import com.bupt.ecommerce.product.entity.SeckillReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeckillReservationRepository extends JpaRepository<SeckillReservation, Long> {

    Optional<SeckillReservation> findByMessageId(String messageId);

    Optional<SeckillReservation> findByActivityIdAndUserId(Long activityId, Long userId);
}
