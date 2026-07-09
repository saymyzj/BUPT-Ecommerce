package com.bupt.ecommerce.product.repository;

import com.bupt.ecommerce.product.entity.SeckillWaitlistEntry;
import com.bupt.ecommerce.product.entity.WaitlistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeckillWaitlistRepository extends JpaRepository<SeckillWaitlistEntry, Long> {

    Optional<SeckillWaitlistEntry> findByActivityIdAndUserId(Long activityId, Long userId);

    long countByActivityIdAndStatus(Long activityId, WaitlistStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select w from SeckillWaitlistEntry w
            where w.activityId = :activityId and w.status = :status
            order by w.id asc
            """)
    List<SeckillWaitlistEntry> findForPromotion(
            @Param("activityId") Long activityId,
            @Param("status") WaitlistStatus status
    );

    @Query("select distinct w.activityId from SeckillWaitlistEntry w where w.status = :status")
    List<Long> findActivityIdsByStatus(@Param("status") WaitlistStatus status);
}
