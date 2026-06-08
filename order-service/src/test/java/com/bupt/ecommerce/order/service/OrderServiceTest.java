package com.bupt.ecommerce.order.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.entity.MqMessageLog;
import com.bupt.ecommerce.order.entity.MqMessageStatus;
import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.entity.OrderStatus;
import com.bupt.ecommerce.order.repository.MqMessageLogRepository;
import com.bupt.ecommerce.order.repository.OrderItemRepository;
import com.bupt.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private MqMessageLogRepository messageLogRepository;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        messageLogRepository = mock(MqMessageLogRepository.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                messageLogRepository,
                redisTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void createFromMessageShouldReturnExistingOrderWhenMessageAlreadyProcessed() {
        OrderCreateMessage message = message();
        MqMessageLog processedLog = new MqMessageLog();
        processedLog.setMessageId(message.messageId());
        processedLog.setStatus(MqMessageStatus.PROCESSED);
        Order existing = order();

        when(messageLogRepository.findByMessageId(message.messageId())).thenReturn(Optional.of(processedLog));
        when(orderRepository.findByOrderNo(message.orderNo())).thenReturn(Optional.of(existing));

        Order result = orderService.createFromMessage(message, "{}");

        assertSame(existing, result);
        verify(orderRepository, never()).save(org.mockito.ArgumentMatchers.any(Order.class));
        verify(orderItemRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void detailShouldRejectOtherUsersOrder() {
        Order order = order();
        order.setUserId(10002L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> orderService.detail(1L, 10001L));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
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
                "ORD202606060001",
                LocalDateTime.now()
        );
    }

    private Order order() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD202606060001");
        order.setUserId(10001L);
        order.setActivityId(1L);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(new BigDecimal("99.90"));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }
}
