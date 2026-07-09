package com.bupt.ecommerce.order.mq;

import com.bupt.ecommerce.order.config.RabbitMqConfig;
import com.bupt.ecommerce.order.service.DeadLetterService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class DeadLetterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterConsumer.class);

    private final DeadLetterService deadLetterService;

    public DeadLetterConsumer(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_CREATE_DLQ)
    public void consume(
            Message rabbitMessage,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws Exception {
        String payload = new String(rabbitMessage.getBody(), StandardCharsets.UTF_8);
        deadLetterService.record(rabbitMessage, payload);
        channel.basicAck(deliveryTag, false);
        log.warn("captured seckill order dead letter messageId={}",
                rabbitMessage.getMessageProperties().getMessageId());
    }
}
