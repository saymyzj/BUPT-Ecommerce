package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.dto.OrderCreateMessage;
import com.bupt.ecommerce.product.entity.PublishEventStatus;
import com.bupt.ecommerce.product.entity.SeckillPublishEvent;
import com.bupt.ecommerce.product.entity.SeckillReservationStatus;
import com.bupt.ecommerce.product.mq.OrderMessagePublisher;
import com.bupt.ecommerce.product.repository.SeckillPublishEventRepository;
import com.bupt.ecommerce.product.repository.SeckillReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReliableOrderPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReliableOrderPublisher.class);

    private final SeckillPublishEventRepository eventRepository;
    private final SeckillReservationRepository reservationRepository;
    private final OrderMessagePublisher publisher;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public ReliableOrderPublisher(
            SeckillPublishEventRepository eventRepository,
            SeckillReservationRepository reservationRepository,
            OrderMessagePublisher publisher,
            ObjectMapper objectMapper,
            @Value("${app.seckill-publisher.max-attempts:12}") int maxAttempts
    ) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
    }

    public void publishNow(SeckillPublishEvent event, OrderCreateMessage message) {
        try {
            publisher.publish(message);
            markSent(event);
        } catch (RuntimeException ex) {
            markUnknown(event, ex);
            log.warn("seckill publish outcome unknown messageId={}, scheduled for retry", message.messageId(), ex);
        }
    }

    @Scheduled(fixedDelayString = "${app.seckill-publisher.fixed-delay-ms:5000}")
    public void retryPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        for (SeckillPublishEvent event : eventRepository
                .findTop20ByStatusInAndNextRetryAtLessThanEqualOrderByUpdatedAtAsc(
                        List.of(PublishEventStatus.PENDING, PublishEventStatus.UNKNOWN),
                        now
                )) {
            try {
                OrderCreateMessage message = objectMapper.readValue(event.getPayload(), OrderCreateMessage.class);
                publisher.publish(message);
                markSent(event);
            } catch (Exception ex) {
                markUnknown(event, ex);
            }
        }
    }

    private void markSent(SeckillPublishEvent event) {
        event.setStatus(PublishEventStatus.SENT);
        event.setNextRetryAt(null);
        event.setLastError(null);
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    private void markUnknown(SeckillPublishEvent event, Exception failure) {
        int attempts = event.getRetryCount() == null ? 1 : event.getRetryCount() + 1;
        LocalDateTime now = LocalDateTime.now();
        event.setRetryCount(attempts);
        event.setLastError(limit(failure.getMessage()));
        event.setUpdatedAt(now);
        if (attempts >= maxAttempts) {
            event.setStatus(PublishEventStatus.DEAD);
            event.setNextRetryAt(null);
            reservationRepository.findByMessageId(event.getMessageId()).ifPresent(reservation -> {
                if (reservation.getStatus() != SeckillReservationStatus.CREATED
                        && reservation.getStatus() != SeckillReservationStatus.RELEASED) {
                    reservation.setStatus(SeckillReservationStatus.DEAD);
                    reservation.setFailureCode("PUBLISH_OUTCOME_UNKNOWN");
                    reservation.setFailureReason(event.getLastError());
                    reservation.setUpdatedAt(now);
                    reservationRepository.save(reservation);
                }
            });
        } else {
            event.setStatus(PublishEventStatus.UNKNOWN);
            event.setNextRetryAt(now.plusSeconds(backoffSeconds(attempts)));
        }
        eventRepository.save(event);
    }

    private long backoffSeconds(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 6));
        return Math.min(5L * (1L << exponent), 300L);
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
