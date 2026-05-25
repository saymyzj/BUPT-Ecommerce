package com.bupt.ecommerce.order.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.order.dto.OrderCreateMessage;
import com.bupt.ecommerce.order.dto.OrderResponse;
import com.bupt.ecommerce.order.dto.SeckillResultResponse;
import com.bupt.ecommerce.order.entity.MqMessageLog;
import com.bupt.ecommerce.order.entity.MqMessageStatus;
import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.entity.OrderItem;
import com.bupt.ecommerce.order.entity.OrderStatus;
import com.bupt.ecommerce.order.redis.SeckillRedisKeys;
import com.bupt.ecommerce.order.repository.MqMessageLogRepository;
import com.bupt.ecommerce.order.repository.OrderItemRepository;
import com.bupt.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private static final Duration RESULT_TTL = Duration.ofHours(24);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MqMessageLogRepository messageLogRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MqMessageLogRepository messageLogRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.messageLogRepository = messageLogRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createFromMessage(OrderCreateMessage message, String rawPayload) {
        Order processed = messageLogRepository.findByMessageId(message.messageId())
                .filter(log -> log.getStatus() == MqMessageStatus.PROCESSED)
                .flatMap(log -> orderRepository.findByOrderNo(message.orderNo()))
                .orElse(null);
        if (processed != null) {
            return processed;
        }

        Order existingOrder = orderRepository.findByUserIdAndActivityId(message.userId(), message.activityId())
                .or(() -> orderRepository.findByOrderNo(message.orderNo()))
                .orElse(null);
        if (existingOrder != null) {
            upsertMessageLog(message, rawPayload, MqMessageStatus.PROCESSED);
            return existingOrder;
        }

        MqMessageLog log = upsertMessageLog(message, rawPayload, MqMessageStatus.RECEIVED);
        LocalDateTime now = LocalDateTime.now();

        Order order = new Order();
        order.setOrderNo(message.orderNo());
        order.setUserId(message.userId());
        order.setActivityId(message.activityId());
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(message.seckillPrice().multiply(BigDecimal.valueOf(message.quantity())));
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        Order savedOrder = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(savedOrder.getId());
        item.setProductId(message.productId());
        item.setQuantity(message.quantity());
        item.setUnitPrice(message.seckillPrice());
        item.setCreatedAt(now);
        orderItemRepository.save(item);

        log.setStatus(MqMessageStatus.PROCESSED);
        log.setUpdatedAt(now);
        messageLogRepository.save(log);
        return savedOrder;
    }

    @Transactional
    public void markMessageFailed(OrderCreateMessage message, String rawPayload) {
        upsertMessageLog(message, rawPayload, MqMessageStatus.FAILED);
    }

    public void writeCreatedResult(Order order) {
        SeckillResultResponse result = new SeckillResultResponse(
                order.getActivityId(),
                order.getStatus().name(),
                order.getId(),
                order.getOrderNo(),
                "订单创建成功"
        );
        try {
            String value = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(SeckillRedisKeys.result(order.getActivityId(), order.getUserId()), value, RESULT_TTL);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    @Transactional(readOnly = true)
    public SeckillResultResponse findSeckillResult(Long activityId, Long userId) {
        return orderRepository.findByUserIdAndActivityId(userId, activityId)
                .map(order -> new SeckillResultResponse(
                        order.getActivityId(),
                        order.getStatus().name(),
                        order.getId(),
                        order.getOrderNo(),
                        "订单创建成功"
                ))
                .orElseGet(() -> new SeckillResultResponse(activityId, "QUEUEING", null, null, "queued"));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> page(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(pageSize, 1));
        Page<Order> result = orderRepository.findByUserId(userId, pageable);
        return new PageResponse<>(
                result.getContent().stream().map(OrderResponse::summary).toList(),
                page,
                pageSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public OrderResponse detail(Long orderId, Long userId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        return OrderResponse.from(order, items);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> adminPage(int page, int pageSize, String status) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(pageSize, 1));
        Page<Order> result;
        if (status == null || status.isBlank()) {
            result = orderRepository.findAll(pageable);
        } else {
            result = orderRepository.findByStatus(OrderStatus.valueOf(status), pageable);
        }
        return new PageResponse<>(
                result.getContent().stream().map(OrderResponse::summary).toList(),
                page,
                pageSize,
                result.getTotalElements()
        );
    }

    private MqMessageLog upsertMessageLog(OrderCreateMessage message, String rawPayload, MqMessageStatus status) {
        LocalDateTime now = LocalDateTime.now();
        String businessKey = "SECKILL_ORDER:%d:%d".formatted(message.activityId(), message.userId());
        MqMessageLog log = messageLogRepository.findByMessageId(message.messageId())
                .or(() -> messageLogRepository.findByBusinessKey(businessKey))
                .orElseGet(MqMessageLog::new);
        log.setMessageId(log.getMessageId() == null ? message.messageId() : log.getMessageId());
        log.setEventType(message.eventType());
        log.setBusinessKey(businessKey);
        log.setStatus(status);
        log.setRetryCount(log.getRetryCount() == null ? 0 : log.getRetryCount() + (status == MqMessageStatus.FAILED ? 1 : 0));
        log.setPayload(rawPayload);
        log.setCreatedAt(log.getCreatedAt() == null ? now : log.getCreatedAt());
        log.setUpdatedAt(now);
        return messageLogRepository.save(log);
    }
}
