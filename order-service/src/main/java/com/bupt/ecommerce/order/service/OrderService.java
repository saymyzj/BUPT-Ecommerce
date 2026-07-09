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
import com.bupt.ecommerce.order.entity.SeckillReservation;
import com.bupt.ecommerce.order.entity.SeckillReservationStatus;
import com.bupt.ecommerce.order.exception.ReservationUnavailableException;
import com.bupt.ecommerce.order.redis.SeckillRedisKeys;
import com.bupt.ecommerce.order.repository.MqMessageLogRepository;
import com.bupt.ecommerce.order.repository.OrderItemRepository;
import com.bupt.ecommerce.order.repository.OrderRepository;
import com.bupt.ecommerce.order.repository.SeckillReservationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private static final Duration RESULT_TTL = Duration.ofHours(24);
    private static final String RELEASE_LUA_SCRIPT = """
            if redis.call('EXISTS', KEYS[4]) == 1 then
                return 0
            end
            if redis.call('EXISTS', KEYS[1]) == 0 then
                return -1
            end
            redis.call('SET', KEYS[4], '1', 'EX', ARGV[1])
            redis.call('INCR', KEYS[1])
            redis.call('DEL', KEYS[2])
            redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[1])
            return 1
            """;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MqMessageLogRepository messageLogRepository;
    private final SeckillReservationRepository reservationRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            MqMessageLogRepository messageLogRepository,
            SeckillReservationRepository reservationRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.messageLogRepository = messageLogRepository;
        this.reservationRepository = reservationRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createFromMessage(OrderCreateMessage message, String rawPayload) {
        SeckillReservation reservation = reservationRepository.findByMessageIdForUpdate(message.messageId())
                .orElse(null);
        if (reservation != null && (reservation.getStatus() == SeckillReservationStatus.RELEASE_PENDING
                || reservation.getStatus() == SeckillReservationStatus.RELEASED
                || reservation.getStatus() == SeckillReservationStatus.DEAD)) {
            throw new ReservationUnavailableException("seckill reservation is no longer orderable");
        }

        Order processed = messageLogRepository.findByMessageId(message.messageId())
                .filter(log -> log.getStatus() == MqMessageStatus.PROCESSED)
                .flatMap(log -> orderRepository.findByOrderNo(message.orderNo()))
                .orElse(null);
        if (processed != null) {
            markReservationCreated(reservation, processed.getId());
            return processed;
        }

        Order existingOrder = orderRepository.findByUserIdAndActivityId(message.userId(), message.activityId())
                .or(() -> orderRepository.findByOrderNo(message.orderNo()))
                .orElse(null);
        if (existingOrder != null) {
            upsertMessageLog(message, rawPayload, MqMessageStatus.PROCESSED);
            markReservationCreated(reservation, existingOrder.getId());
            return existingOrder;
        }

        MqMessageLog log = upsertMessageLog(message, rawPayload, MqMessageStatus.RECEIVED);
        LocalDateTime now = LocalDateTime.now();
        if (reservation != null) {
            reservation.setStatus(SeckillReservationStatus.ORDERING);
            reservation.setFailureCode(null);
            reservation.setFailureReason(null);
            reservation.setNextRetryAt(null);
            reservation.setUpdatedAt(now);
            reservationRepository.save(reservation);
        }

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
        markReservationCreated(reservation, savedOrder.getId());
        return savedOrder;
    }

    @Transactional
    public MqMessageLog markMessageFailed(OrderCreateMessage message, String rawPayload) {
        return markMessageFailed(message, rawPayload, null);
    }

    @Transactional
    public MqMessageLog markMessageFailed(OrderCreateMessage message, String rawPayload, Throwable failure) {
        MqMessageLog log = upsertMessageLog(message, rawPayload, MqMessageStatus.FAILED);
        reservationRepository.findByMessageIdForUpdate(message.messageId()).ifPresent(reservation -> {
            if (reservation.getStatus() == SeckillReservationStatus.CREATED
                    || reservation.getStatus() == SeckillReservationStatus.RELEASED) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            int retryCount = log.getRetryCount() == null ? 0 : log.getRetryCount();
            reservation.setStatus(SeckillReservationStatus.RETRYING);
            reservation.setRetryCount(retryCount);
            reservation.setFailureCode(failure == null ? "ORDER_PROCESSING_FAILED" : failure.getClass().getSimpleName());
            reservation.setFailureReason(limitMessage(failure));
            reservation.setNextRetryAt(now.plusSeconds(backoffSeconds(retryCount)));
            reservation.setUpdatedAt(now);
            reservationRepository.save(reservation);
        });
        return log;
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
    public boolean isRetryDue(OrderCreateMessage message, LocalDateTime now) {
        return reservationRepository.findByMessageId(message.messageId())
                .map(reservation -> reservation.getNextRetryAt() == null
                        || !reservation.getNextRetryAt().isAfter(now))
                .orElse(true);
    }

    @Transactional
    public SeckillReservation preparePermanentFailure(OrderCreateMessage message, String reason) {
        Order existingOrder = orderRepository.findByUserIdAndActivityId(message.userId(), message.activityId())
                .or(() -> orderRepository.findByOrderNo(message.orderNo()))
                .orElse(null);
        SeckillReservation reservation = reservationRepository.findByMessageIdForUpdate(message.messageId())
                .orElse(null);
        if (existingOrder != null) {
            markReservationCreated(reservation, existingOrder.getId());
            upsertMessageLog(message, writeMessage(message), MqMessageStatus.PROCESSED);
            return null;
        }
        if (reservation == null
                || reservation.getStatus() == SeckillReservationStatus.RELEASED
                || reservation.getStatus() == SeckillReservationStatus.CREATED) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        reservation.setStatus(SeckillReservationStatus.RELEASE_PENDING);
        reservation.setFailureCode("RETRY_EXHAUSTED");
        reservation.setFailureReason(limit(reason));
        reservation.setNextRetryAt(now);
        reservation.setUpdatedAt(now);
        return reservationRepository.save(reservation);
    }

    public boolean releasePendingReservation(SeckillReservation reservation) {
        String failedResult = writeResultJson(new SeckillResultResponse(
                reservation.getActivityId(),
                "FAILED",
                null,
                reservation.getOrderNo(),
                "订单创建失败，秒杀资格已释放"
        ));
        Long released = redisTemplate.execute(
                new DefaultRedisScript<>(RELEASE_LUA_SCRIPT, Long.class),
                List.of(
                        SeckillRedisKeys.stock(reservation.getActivityId()),
                        SeckillRedisKeys.user(reservation.getActivityId(), reservation.getUserId()),
                        SeckillRedisKeys.result(reservation.getActivityId(), reservation.getUserId()),
                        SeckillRedisKeys.compensated(reservation.getRequestId())
                ),
                String.valueOf(RESULT_TTL.toSeconds()),
                failedResult
        );
        if (released == null || released < 0) {
            return false;
        }
        markReservationReleased(reservation.getId());
        return true;
    }

    @Transactional
    public void markReservationReleased(Long reservationId) {
        reservationRepository.findById(reservationId).ifPresent(reservation -> {
            if (reservation.getStatus() == SeckillReservationStatus.RELEASED) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            reservation.setStatus(SeckillReservationStatus.RELEASED);
            reservation.setReleasedAt(now);
            reservation.setNextRetryAt(null);
            reservation.setUpdatedAt(now);
            reservationRepository.save(reservation);
        });
    }

    @Transactional(readOnly = true)
    public List<SeckillReservation> findReleasePending(LocalDateTime now) {
        return reservationRepository.findTop20ByStatusAndNextRetryAtLessThanEqualOrderByUpdatedAtAsc(
                SeckillReservationStatus.RELEASE_PENDING,
                now
        );
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

    private void markReservationCreated(SeckillReservation reservation, Long orderId) {
        if (reservation == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        reservation.setStatus(SeckillReservationStatus.CREATED);
        reservation.setFailureCode(null);
        reservation.setFailureReason(null);
        reservation.setNextRetryAt(null);
        reservation.setUpdatedAt(now);
        reservationRepository.save(reservation);
    }

    private long backoffSeconds(int retryCount) {
        int exponent = Math.max(0, Math.min(retryCount - 1, 5));
        return Math.min(30L * (1L << exponent), 900L);
    }

    private String limitMessage(Throwable failure) {
        if (failure == null) {
            return "订单处理失败";
        }
        String message = failure.getMessage();
        return limit(message == null || message.isBlank() ? failure.getClass().getSimpleName() : message);
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private String writeResultJson(SeckillResultResponse result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String writeMessage(OrderCreateMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }
}
