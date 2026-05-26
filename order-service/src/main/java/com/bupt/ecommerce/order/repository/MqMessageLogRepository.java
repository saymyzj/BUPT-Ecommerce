package com.bupt.ecommerce.order.repository;

import com.bupt.ecommerce.order.entity.MqMessageLog;
import com.bupt.ecommerce.order.entity.MqMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MqMessageLogRepository extends JpaRepository<MqMessageLog, Long> {

    Optional<MqMessageLog> findByMessageId(String messageId);

    Optional<MqMessageLog> findByBusinessKey(String businessKey);

    List<MqMessageLog> findTop20ByStatusAndRetryCountLessThanOrderByUpdatedAtAsc(
            MqMessageStatus status,
            Integer retryCount
    );
}
