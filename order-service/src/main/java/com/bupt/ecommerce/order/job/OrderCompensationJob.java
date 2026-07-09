package com.bupt.ecommerce.order.job;

import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.entity.MqMessageLog;
import com.bupt.ecommerce.order.entity.MqMessageStatus;
import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.entity.SeckillReservation;
import com.bupt.ecommerce.order.repository.MqMessageLogRepository;
import com.bupt.ecommerce.order.service.OrderService;
import com.bupt.ecommerce.order.service.TaskLeaseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "app.order-compensation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderCompensationJob {

    private static final Logger log = LoggerFactory.getLogger(OrderCompensationJob.class);

    private final MqMessageLogRepository messageLogRepository;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final TaskLeaseService taskLeaseService;
    private final int maxAttempts;

    public OrderCompensationJob(
            MqMessageLogRepository messageLogRepository,
            OrderService orderService,
            ObjectMapper objectMapper,
            TaskLeaseService taskLeaseService,
            @Value("${app.order-compensation.max-attempts:6}") int maxAttempts
    ) {
        this.messageLogRepository = messageLogRepository;
        this.orderService = orderService;
        this.objectMapper = objectMapper;
        this.taskLeaseService = taskLeaseService;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.order-compensation.fixed-delay-ms:60000}")
    public void compensateFailedMessages() {
        String taskName = "order-compensation";
        if (!taskLeaseService.tryAcquire(taskName, Duration.ofMinutes(5))) {
            return;
        }
        try {
            compensateUnderLease();
        } finally {
            taskLeaseService.release(taskName);
        }
    }

    private void compensateUnderLease() {
        LocalDateTime now = LocalDateTime.now();
        for (MqMessageLog logEntry : messageLogRepository
                .findTop20ByStatusAndRetryCountLessThanOrderByUpdatedAtAsc(MqMessageStatus.FAILED, maxAttempts)) {
            try {
                OrderCreateMessage message = objectMapper.readValue(logEntry.getPayload(), OrderCreateMessage.class);
                if (!orderService.isRetryDue(message, now)) {
                    continue;
                }
                Order order = orderService.createFromMessage(message, logEntry.getPayload());
                orderService.writeCreatedResult(order);
                log.info("compensated seckill order messageId={} orderNo={}", message.messageId(), order.getOrderNo());
            } catch (Exception ex) {
                tryMarkFailed(logEntry, ex);
            }
        }
        releaseTerminalReservations(now);
    }

    private void tryMarkFailed(MqMessageLog logEntry, Exception ex) {
        try {
            OrderCreateMessage message = objectMapper.readValue(logEntry.getPayload(), OrderCreateMessage.class);
            MqMessageLog failed = orderService.markMessageFailed(message, logEntry.getPayload(), ex);
            if (failed.getRetryCount() >= maxAttempts) {
                SeckillReservation reservation = orderService.preparePermanentFailure(message, ex.getMessage());
                if (reservation != null) {
                    orderService.releasePendingReservation(reservation);
                }
            }
            log.warn("failed to compensate seckill order messageId={}", logEntry.getMessageId(), ex);
        } catch (Exception parseEx) {
            log.error("failed to parse failed mq payload during compensation id={}", logEntry.getId(), parseEx);
        }
    }

    private void releaseTerminalReservations(LocalDateTime now) {
        for (SeckillReservation reservation : orderService.findReleasePending(now)) {
            try {
                if (!orderService.releasePendingReservation(reservation)) {
                    log.warn("release pending reservation waiting for Redis requestId={}", reservation.getRequestId());
                }
            } catch (Exception ex) {
                log.error("failed to release terminal reservation requestId={}", reservation.getRequestId(), ex);
            }
        }
    }
}
