package com.bupt.ecommerce.product.repository;

import com.bupt.ecommerce.product.entity.SeckillReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import com.bupt.ecommerce.product.entity.SeckillReservationStatus;

public interface SeckillReservationRepository extends JpaRepository<SeckillReservation, Long> {

    Optional<SeckillReservation> findByMessageId(String messageId);

    Optional<SeckillReservation> findByActivityIdAndUserId(Long activityId, Long userId);

    List<SeckillReservation> findByActivityIdAndStatusIn(
            Long activityId,
            Collection<SeckillReservationStatus> statuses
    );
}
