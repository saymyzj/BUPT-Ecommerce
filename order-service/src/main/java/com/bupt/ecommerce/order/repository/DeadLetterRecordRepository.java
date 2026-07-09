package com.bupt.ecommerce.order.repository;

import com.bupt.ecommerce.order.entity.DeadLetterRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeadLetterRecordRepository extends JpaRepository<DeadLetterRecord, Long> {

    Optional<DeadLetterRecord> findByMessageId(String messageId);
}
