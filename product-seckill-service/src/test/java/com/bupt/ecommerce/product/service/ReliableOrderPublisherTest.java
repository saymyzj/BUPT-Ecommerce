package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.dto.OrderCreateMessage;
import com.bupt.ecommerce.product.entity.PublishEventStatus;
import com.bupt.ecommerce.product.entity.SeckillPublishEvent;
import com.bupt.ecommerce.product.mq.OrderMessagePublisher;
import com.bupt.ecommerce.product.repository.SeckillPublishEventRepository;
import com.bupt.ecommerce.product.repository.SeckillReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReliableOrderPublisherTest {

    @Test
    void uncertainConfirmShouldPersistRetryWithoutReturningFailureToAcceptedUser() {
        SeckillPublishEventRepository eventRepository = mock(SeckillPublishEventRepository.class);
        SeckillReservationRepository reservationRepository = mock(SeckillReservationRepository.class);
        OrderMessagePublisher publisher = mock(OrderMessagePublisher.class);
        ReliableOrderPublisher reliablePublisher = new ReliableOrderPublisher(
                eventRepository,
                reservationRepository,
                publisher,
                new ObjectMapper().findAndRegisterModules(),
                mock(TaskLeaseService.class),
                12
        );
        SeckillPublishEvent event = event();
        OrderCreateMessage message = message();
        when(eventRepository.save(any(SeckillPublishEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("confirm timeout")).when(publisher).publish(message);

        assertDoesNotThrow(() -> reliablePublisher.publishNow(event, message));

        assertEquals(PublishEventStatus.UNKNOWN, event.getStatus());
        assertEquals(1, event.getRetryCount());
    }

    @Test
    void confirmedPublishShouldBecomeSent() {
        SeckillPublishEventRepository eventRepository = mock(SeckillPublishEventRepository.class);
        ReliableOrderPublisher reliablePublisher = new ReliableOrderPublisher(
                eventRepository,
                mock(SeckillReservationRepository.class),
                mock(OrderMessagePublisher.class),
                new ObjectMapper().findAndRegisterModules(),
                mock(TaskLeaseService.class),
                12
        );
        SeckillPublishEvent event = event();
        when(eventRepository.save(any(SeckillPublishEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        reliablePublisher.publishNow(event, message());

        assertEquals(PublishEventStatus.SENT, event.getStatus());
    }

    private SeckillPublishEvent event() {
        SeckillPublishEvent event = new SeckillPublishEvent();
        event.setMessageId("message-1");
        event.setPayload("{}");
        event.setStatus(PublishEventStatus.PENDING);
        event.setRetryCount(0);
        event.setNextRetryAt(LocalDateTime.now());
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        return event;
    }

    private OrderCreateMessage message() {
        return new OrderCreateMessage(
                "message-1",
                "SECKILL_ORDER_CREATE",
                1L,
                10001L,
                20001L,
                1,
                new BigDecimal("99.90"),
                "ORD202607090001",
                LocalDateTime.now(),
                "request-1"
        );
    }
}
