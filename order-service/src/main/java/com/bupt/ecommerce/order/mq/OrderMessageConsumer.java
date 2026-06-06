package com.bupt.ecommerce.order.mq;

import com.bupt.ecommerce.order.config.RabbitMqConfig;
import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.push.OrderPushClient;
import com.bupt.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class OrderMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final OrderService orderService;
    private final OrderPushClient orderPushClient;
    private final ObjectMapper objectMapper;

    public OrderMessageConsumer(OrderService orderService, OrderPushClient orderPushClient, ObjectMapper objectMapper) {
        this.orderService = orderService;
        this.orderPushClient = orderPushClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_CREATE_QUEUE)
    public void consume(Message rabbitMessage, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        String payload = new String(rabbitMessage.getBody(), StandardCharsets.UTF_8);
        OrderCreateMessage message = null;
        try {
            message = objectMapper.readValue(payload, OrderCreateMessage.class);
        } catch (Exception ex) {
            log.error("failed to parse seckill order message payload={}", payload, ex);
            throw ex;
        }

        try {
            Order order = orderService.createFromMessage(message, payload);
            orderService.writeCreatedResult(order);
            orderPushClient.publishCreated(order);
            channel.basicAck(deliveryTag, false);
            log.info("created seckill order orderNo={} messageId={}", order.getOrderNo(), message.messageId());
        } catch (Exception ex) {
            orderService.markMessageFailed(message, payload);
            log.error("failed to consume seckill order message={}", message.messageId(), ex);
            throw ex;
        }
    }
}
