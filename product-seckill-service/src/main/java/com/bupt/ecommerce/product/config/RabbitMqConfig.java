package com.bupt.ecommerce.product.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMqConfig {

    public static final String ORDER_EXCHANGE = "seckill.order.exchange";
    public static final String ORDER_CREATE_QUEUE = "seckill.order.create.queue";
    public static final String ORDER_CREATE_DLX = "seckill.order.create.dlx";
    public static final String ORDER_CREATE_DLQ = "seckill.order.create.dlq";
    public static final String ORDER_CREATE_ROUTING_KEY = "seckill.order.create";

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    public TopicExchange seckillOrderExchange() {
        return new TopicExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange seckillOrderDlx() {
        return new DirectExchange(ORDER_CREATE_DLX, true, false);
    }

    @Bean
    public Queue seckillOrderCreateQueue() {
        return QueueBuilder.durable(ORDER_CREATE_QUEUE)
                .deadLetterExchange(ORDER_CREATE_DLX)
                .deadLetterRoutingKey(ORDER_CREATE_QUEUE)
                .build();
    }

    @Bean
    public Queue seckillOrderCreateDlq() {
        return QueueBuilder.durable(ORDER_CREATE_DLQ).build();
    }

    @Bean
    public Binding seckillOrderBinding(
            @Qualifier("seckillOrderCreateQueue") Queue seckillOrderCreateQueue,
            TopicExchange seckillOrderExchange
    ) {
        return BindingBuilder.bind(seckillOrderCreateQueue)
                .to(seckillOrderExchange)
                .with(ORDER_CREATE_ROUTING_KEY);
    }

    @Bean
    public Binding seckillOrderDlqBinding(
            @Qualifier("seckillOrderCreateDlq") Queue seckillOrderCreateDlq,
            DirectExchange seckillOrderDlx
    ) {
        return BindingBuilder.bind(seckillOrderCreateDlq)
                .to(seckillOrderDlx)
                .with(ORDER_CREATE_QUEUE);
    }

    @Bean
    public RetryOperationsInterceptor rabbitRetryInterceptor() {
        return RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }
}
