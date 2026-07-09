package com.bupt.ecommerce.order.mq;

import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.push.OrderPushClient;
import com.bupt.ecommerce.order.service.DeadLetterService;
import com.bupt.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderMessageConsumerTest {

    private final OrderService orderService = mock(OrderService.class);
    private final Channel channel = mock(Channel.class);
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OrderMessageConsumer consumer = new OrderMessageConsumer(
            orderService,
            mock(OrderPushClient.class),
            mock(DeadLetterService.class),
            objectMapper
    );

    @Test
    void malformedMessageShouldBeRejectedToDeadLetterQueue() throws Exception {
        Message rabbitMessage = MessageBuilder
                .withBody("not-json".getBytes(StandardCharsets.UTF_8))
                .build();

        consumer.consume(rabbitMessage, channel, 11L);

        verify(channel).basicNack(11L, false, false);
        verify(channel, never()).basicAck(any(Long.class), any(Boolean.class));
    }

    @Test
    void processingFailureShouldBeRecordedThenRejectedToDeadLetterQueue() throws Exception {
        OrderCreateMessage orderMessage = new OrderCreateMessage(
                "message-1",
                "SECKILL_ORDER_CREATE",
                1L,
                2L,
                3L,
                1,
                new BigDecimal("9.90"),
                "ORDER-1",
                LocalDateTime.now(),
                "request-1"
        );
        String payload = objectMapper.writeValueAsString(orderMessage);
        Message rabbitMessage = MessageBuilder
                .withBody(payload.getBytes(StandardCharsets.UTF_8))
                .build();
        when(orderService.createFromMessage(any(OrderCreateMessage.class), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));

        consumer.consume(rabbitMessage, channel, 12L);

        verify(orderService).markMessageFailed(any(OrderCreateMessage.class), anyString(), any());
        verify(channel).basicNack(12L, false, false);
    }

    @Test
    void failureRecordOutageShouldStillRejectMessageToDurableDeadLetterQueue() throws Exception {
        OrderCreateMessage orderMessage = new OrderCreateMessage(
                "message-2",
                "SECKILL_ORDER_CREATE",
                1L,
                2L,
                3L,
                1,
                new BigDecimal("9.90"),
                "ORDER-2",
                LocalDateTime.now(),
                "request-2"
        );
        String payload = objectMapper.writeValueAsString(orderMessage);
        Message rabbitMessage = MessageBuilder
                .withBody(payload.getBytes(StandardCharsets.UTF_8))
                .build();
        when(orderService.createFromMessage(any(OrderCreateMessage.class), anyString()))
                .thenThrow(new IllegalStateException("database unavailable"));
        org.mockito.Mockito.doThrow(new IllegalStateException("failure log unavailable"))
                .when(orderService).markMessageFailed(any(OrderCreateMessage.class), anyString(), any());

        consumer.consume(rabbitMessage, channel, 13L);

        verify(channel).basicNack(13L, false, false);
    }
}
