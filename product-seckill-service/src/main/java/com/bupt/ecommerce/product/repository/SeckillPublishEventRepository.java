package com.bupt.ecommerce.product.repository;

import com.bupt.ecommerce.product.entity.PublishEventStatus;
import com.bupt.ecommerce.product.entity.SeckillPublishEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SeckillPublishEventRepository extends JpaRepository<SeckillPublishEvent, Long> {

    Optional<SeckillPublishEvent> findByMessageId(String messageId);

    List<SeckillPublishEvent> findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByUpdatedAtAsc(
            Collection<PublishEventStatus> statuses,
            LocalDateTime now
    );
}
