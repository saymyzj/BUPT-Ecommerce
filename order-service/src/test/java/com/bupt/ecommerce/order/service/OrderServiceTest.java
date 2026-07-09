package com.bupt.ecommerce.order.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.entity.MqMessageLog;
import com.bupt.ecommerce.order.entity.MqMessageStatus;
import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.entity.OrderStatus;
import com.bupt.ecommerce.order.entity.SeckillReservation;
import com.bupt.ecommerce.order.entity.SeckillReservationStatus;
import com.bupt.ecommerce.order.repository.MqMessageLogRepository;
import com.bupt.ecommerce.order.repository.OrderItemRepository;
import com.bupt.ecommerce.order.repository.OrderRepository;
import com.bupt.ecommerce.order.repository.SeckillReservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

class OrderServiceTest {

    private OrderRepository orderRepository;
    private OrderItemRepository orderItemRepository;
    private MqMessageLogRepository messageLogRepository;
    private SeckillReservationRepository reservationRepository;
    private StringRedisTemplate redisTemplate;
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        orderItemRepository = mock(OrderItemRepository.class);
        messageLogRepository = mock(MqMessageLogRepository.class);
        reservationRepository = mock(SeckillReservationRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                messageLogRepository,
                reservationRepository,
                redisTemplate,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void permanentFailureShouldReleaseReservationOnceThroughIdempotentLua() {
        OrderCreateMessage message = message();
        SeckillReservation reservation = reservation();
        when(orderRepository.findByUserIdAndActivityId(message.userId(), message.activityId()))
                .thenReturn(Optional.empty());
        when(orderRepository.findByOrderNo(message.orderNo())).thenReturn(Optional.empty());
        when(reservationRepository.findByMessageIdForUpdate(message.messageId())).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(SeckillReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.findById(1L)).thenReturn(Optional.of(reservation));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any()))
                .thenReturn(1L, 0L);

        SeckillReservation pending = orderService.preparePermanentFailure(message, "database unavailable");
        boolean first = orderService.releasePendingReservation(pending);
        boolean second = orderService.releasePendingReservation(pending);

        assertEquals(SeckillReservationStatus.RELEASED, reservation.getStatus());
        assertEquals(true, first);
        assertEquals(true, second);
    }

    @Test
    void permanentFailureShouldNeverReleaseWhenOrderAlreadyExists() {
        OrderCreateMessage message = message();
        Order existing = order();
        SeckillReservation reservation = reservation();
        when(orderRepository.findByUserIdAndActivityId(message.userId(), message.activityId()))
                .thenReturn(Optional.of(existing));
        when(reservationRepository.findByMessageIdForUpdate(message.messageId())).thenReturn(Optional.of(reservation));
        when(messageLogRepository.findByMessageId(message.messageId())).thenReturn(Optional.empty());
        when(messageLogRepository.findByBusinessKey(any())).thenReturn(Optional.empty());
        when(messageLogRepository.save(any(MqMessageLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SeckillReservation pending = orderService.preparePermanentFailure(message, "timeout");

        assertNull(pending);
        assertEquals(SeckillReservationStatus.CREATED, reservation.getStatus());
        verifyNoInteractions(redisTemplate);
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

    private SeckillReservation reservation() {
        SeckillReservation reservation = new SeckillReservation();
        reservation.setId(1L);
        reservation.setRequestId("request-1");
        reservation.setMessageId("message-1");
        reservation.setActivityId(1L);
        reservation.setUserId(10001L);
        reservation.setProductId(20001L);
        reservation.setOrderNo("ORD202606060001");
        reservation.setStatus(SeckillReservationStatus.RETRYING);
        reservation.setRetryCount(6);
        reservation.setCreatedAt(LocalDateTime.now());
        reservation.setUpdatedAt(LocalDateTime.now());
        return reservation;
    }
}
