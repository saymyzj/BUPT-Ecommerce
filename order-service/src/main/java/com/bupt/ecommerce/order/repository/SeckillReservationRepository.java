package com.bupt.ecommerce.order.repository;

import com.bupt.ecommerce.order.entity.SeckillReservation;
import com.bupt.ecommerce.order.entity.SeckillReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeckillReservationRepository extends JpaRepository<SeckillReservation, Long> {

    Optional<SeckillReservation> findByMessageId(String messageId);

    Optional<SeckillReservation> findByActivityIdAndUserId(Long activityId, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SeckillReservation r where r.messageId = :messageId")
    Optional<SeckillReservation> findByMessageIdForUpdate(@Param("messageId") String messageId);

    List<SeckillReservation> findTop20ByStatusAndNextRetryAtLessThanEqualOrderByUpdatedAtAsc(
            SeckillReservationStatus status,
            LocalDateTime now
    );
}
