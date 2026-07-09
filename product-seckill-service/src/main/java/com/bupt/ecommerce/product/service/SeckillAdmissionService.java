package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.dto.OrderCreateMessage;
import com.bupt.ecommerce.product.entity.PublishEventStatus;
import com.bupt.ecommerce.product.entity.SeckillPublishEvent;
import com.bupt.ecommerce.product.entity.SeckillReservation;
import com.bupt.ecommerce.product.entity.SeckillReservationStatus;
import com.bupt.ecommerce.product.repository.SeckillPublishEventRepository;
import com.bupt.ecommerce.product.repository.SeckillReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class SeckillAdmissionService {

    private final SeckillReservationRepository reservationRepository;
    private final SeckillPublishEventRepository publishEventRepository;
    private final ObjectMapper objectMapper;

    public SeckillAdmissionService(
            SeckillReservationRepository reservationRepository,
            SeckillPublishEventRepository publishEventRepository,
            ObjectMapper objectMapper
    ) {
        this.reservationRepository = reservationRepository;
        this.publishEventRepository = publishEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SeckillPublishEvent persistAdmission(OrderCreateMessage message) {
        LocalDateTime now = LocalDateTime.now();
        SeckillReservation reservation = new SeckillReservation();
        reservation.setRequestId(message.requestId());
        reservation.setMessageId(message.messageId());
        reservation.setActivityId(message.activityId());
        reservation.setUserId(message.userId());
        reservation.setProductId(message.productId());
        reservation.setOrderNo(message.orderNo());
        reservation.setStatus(SeckillReservationStatus.RESERVED);
        reservation.setRetryCount(0);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);

        SeckillPublishEvent event = new SeckillPublishEvent();
        event.setMessageId(message.messageId());
        event.setPayload(writeMessage(message));
        event.setStatus(PublishEventStatus.PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        return publishEventRepository.save(event);
    }

    private String writeMessage(OrderCreateMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize seckill publish event", ex);
        }
    }
}
