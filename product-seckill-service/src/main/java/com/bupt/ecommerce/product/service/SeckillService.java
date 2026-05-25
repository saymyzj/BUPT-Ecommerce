package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.CreateSeckillActivityRequest;
import com.bupt.ecommerce.product.dto.OrderCreateMessage;
import com.bupt.ecommerce.product.dto.SeckillActivityResponse;
import com.bupt.ecommerce.product.dto.SeckillQueuedResponse;
import com.bupt.ecommerce.product.dto.SeckillRequest;
import com.bupt.ecommerce.product.dto.SeckillResultResponse;
import com.bupt.ecommerce.product.entity.Product;
import com.bupt.ecommerce.product.entity.ProductStatus;
import com.bupt.ecommerce.product.entity.ProductStock;
import com.bupt.ecommerce.product.entity.SeckillActivity;
import com.bupt.ecommerce.product.entity.SeckillActivityStatus;
import com.bupt.ecommerce.product.mq.OrderMessagePublisher;
import com.bupt.ecommerce.product.redis.SeckillRedisKeys;
import com.bupt.ecommerce.product.repository.ProductRepository;
import com.bupt.ecommerce.product.repository.ProductStockRepository;
import com.bupt.ecommerce.product.repository.SeckillActivityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SeckillService {

    private static final String EVENT_TYPE = "SECKILL_ORDER_CREATE";

    private static final String LUA_SCRIPT = """
            if redis.call('EXISTS', KEYS[4]) == 0 then
                return 5
            end
            local startTime = redis.call('HGET', KEYS[4], 'startTime')
            local endTime = redis.call('HGET', KEYS[4], 'endTime')
            local status = redis.call('HGET', KEYS[4], 'status')
            if status == 'FINISHED' or status == 'CANCELLED' then
                return 4
            end
            if startTime and ARGV[1] < startTime then
                return 3
            end
            if endTime and ARGV[1] > endTime then
                return 4
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 2
            end
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then
                return 5
            end
            if stock <= 0 then
                return 1
            end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[3])
            return 0
            """;

    private final ProductRepository productRepository;
    private final ProductStockRepository stockRepository;
    private final SeckillActivityRepository activityRepository;
    private final StringRedisTemplate redisTemplate;
    private final OrderMessagePublisher orderMessagePublisher;
    private final ObjectMapper objectMapper;
    private final RestClient orderServiceClient;
    private final long userFlagTtlSeconds;
    private final long resultTtlSeconds;

    public SeckillService(
            ProductRepository productRepository,
            ProductStockRepository stockRepository,
            SeckillActivityRepository activityRepository,
            StringRedisTemplate redisTemplate,
            OrderMessagePublisher orderMessagePublisher,
            ObjectMapper objectMapper,
            @Value("${app.order-service.base-url}") String orderServiceBaseUrl,
            @Value("${app.seckill.user-flag-ttl-seconds}") long userFlagTtlSeconds,
            @Value("${app.seckill.result-ttl-seconds}") long resultTtlSeconds
    ) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.activityRepository = activityRepository;
        this.redisTemplate = redisTemplate;
        this.orderMessagePublisher = orderMessagePublisher;
        this.objectMapper = objectMapper;
        this.orderServiceClient = RestClient.builder().baseUrl(orderServiceBaseUrl).build();
        this.userFlagTtlSeconds = userFlagTtlSeconds;
        this.resultTtlSeconds = resultTtlSeconds;
    }

    @Transactional
    public SeckillActivityResponse createActivity(CreateSeckillActivityRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        ProductStock stock = stockRepository.findByProductId(product.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (request.seckillStock() > stock.getAvailableStock()) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(product.getId());
        activity.setActivityName("秒杀活动-" + product.getId());
        activity.setSeckillPrice(request.seckillPrice());
        activity.setSeckillStock(request.seckillStock());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
        activity.setStatus(SeckillActivityStatus.READY);
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        SeckillActivity saved = activityRepository.save(activity);
        preheat(saved);
        return SeckillActivityResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public SeckillActivityResponse detail(Long activityId) {
        SeckillActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return SeckillActivityResponse.from(activity);
    }

    public SeckillQueuedResponse seckill(Long activityId, Long userId, SeckillRequest request) {
        if (request.quantity() != 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        SeckillActivity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        preheatIfAbsent(activity);

        String queueingResult = writeResultJson(new SeckillResultResponse(activityId, "QUEUEING", null, null, "queued"));
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                List.of(
                        SeckillRedisKeys.stock(activityId),
                        SeckillRedisKeys.user(activityId, userId),
                        SeckillRedisKeys.result(activityId, userId),
                        SeckillRedisKeys.activity(activityId)
                ),
                LocalDateTime.now().toString(),
                String.valueOf(userFlagTtlSeconds),
                String.valueOf(resultTtlSeconds),
                queueingResult
        );
        mapLuaFailure(result);

        OrderCreateMessage message = new OrderCreateMessage(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                activityId,
                userId,
                activity.getProductId(),
                request.quantity(),
                activity.getSeckillPrice(),
                generateOrderNo(),
                LocalDateTime.now()
        );
        try {
            orderMessagePublisher.publish(message);
            return new SeckillQueuedResponse(activityId, "QUEUEING");
        } catch (RuntimeException ex) {
            rollbackRedis(activityId, userId);
            throw new BusinessException(ErrorCode.MQ_PUBLISH_FAILED);
        }
    }

    public SeckillResultResponse result(Long activityId, Long userId) {
        String value = redisTemplate.opsForValue().get(SeckillRedisKeys.result(activityId, userId));
        if (value == null) {
            return queryOrderResult(activityId, userId);
        }
        try {
            return objectMapper.readValue(value, SeckillResultResponse.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    public void preheat(SeckillActivity activity) {
        redisTemplate.opsForValue().set(SeckillRedisKeys.stock(activity.getId()), String.valueOf(activity.getSeckillStock()));
        cacheActivity(activity);
    }

    private void cacheActivity(SeckillActivity activity) {
        redisTemplate.opsForHash().putAll(
                SeckillRedisKeys.activity(activity.getId()),
                Map.of(
                        "activityId", activity.getId().toString(),
                        "productId", activity.getProductId().toString(),
                        "startTime", activity.getStartTime().toString(),
                        "endTime", activity.getEndTime().toString(),
                        "seckillPrice", activity.getSeckillPrice().toPlainString(),
                        "status", activity.getStatus().name()
                )
        );
        long ttl = Math.max(Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds() + 3600, 3600);
        redisTemplate.expire(SeckillRedisKeys.stock(activity.getId()), Duration.ofSeconds(ttl));
        redisTemplate.expire(SeckillRedisKeys.activity(activity.getId()), Duration.ofSeconds(ttl));
    }

    private void preheatIfAbsent(SeckillActivity activity) {
        Boolean activityExists = redisTemplate.hasKey(SeckillRedisKeys.activity(activity.getId()));
        Boolean stockExists = redisTemplate.hasKey(SeckillRedisKeys.stock(activity.getId()));
        if (!Boolean.TRUE.equals(stockExists)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (!Boolean.TRUE.equals(activityExists)) {
            cacheActivity(activity);
        }
    }

    private SeckillResultResponse queryOrderResult(Long activityId, Long userId) {
        try {
            String value = orderServiceClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/orders/internal/seckill-result")
                            .queryParam("activityId", activityId)
                            .queryParam("userId", userId)
                            .build())
                    .retrieve()
                    .body(String.class);
            if (value == null || value.isBlank()) {
                return queueingResult(activityId);
            }
            var root = objectMapper.readTree(value);
            if (root.path("code").asInt(-1) != 0 || root.path("data").isMissingNode() || root.path("data").isNull()) {
                return queueingResult(activityId);
            }
            return objectMapper.treeToValue(root.path("data"), SeckillResultResponse.class);
        } catch (RestClientException | JsonProcessingException ex) {
            return queueingResult(activityId);
        }
    }

    private SeckillResultResponse queueingResult(Long activityId) {
        return new SeckillResultResponse(activityId, "QUEUEING", null, null, "queued");
    }

    private void rollbackRedis(Long activityId, Long userId) {
        redisTemplate.opsForValue().increment(SeckillRedisKeys.stock(activityId));
        redisTemplate.delete(SeckillRedisKeys.user(activityId, userId));
        String failed = writeResultJson(new SeckillResultResponse(activityId, "FAILED", null, null, "MQ 投递失败"));
        redisTemplate.opsForValue().set(SeckillRedisKeys.result(activityId, userId), failed, Duration.ofSeconds(resultTtlSeconds));
    }

    private void mapLuaFailure(Long result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        if (result == 0) {
            return;
        }
        if (result == 1) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        if (result == 2) {
            throw new BusinessException(ErrorCode.SECKILL_DUPLICATED);
        }
        if (result == 3) {
            throw new BusinessException(ErrorCode.SECKILL_NOT_STARTED);
        }
        if (result == 4) {
            throw new BusinessException(ErrorCode.SECKILL_FINISHED);
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    private String writeResultJson(SeckillResultResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private String generateOrderNo() {
        return "ORD"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
