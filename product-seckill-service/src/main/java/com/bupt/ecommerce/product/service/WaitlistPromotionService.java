package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.dto.OrderCreateMessage;
import com.bupt.ecommerce.product.dto.SeckillResultResponse;
import com.bupt.ecommerce.product.entity.SeckillActivity;
import com.bupt.ecommerce.product.entity.SeckillPublishEvent;
import com.bupt.ecommerce.product.entity.SeckillWaitlistEntry;
import com.bupt.ecommerce.product.entity.WaitlistStatus;
import com.bupt.ecommerce.product.redis.SeckillRedisKeys;
import com.bupt.ecommerce.product.repository.SeckillActivityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WaitlistPromotionService {

    private static final String EVENT_TYPE = "SECKILL_ORDER_CREATE";
    private static final String PROMOTE_LUA = """
            if redis.call('EXISTS', KEYS[4]) == 0 then return 5 end
            local startTime = redis.call('HGET', KEYS[4], 'startTime')
            local endTime = redis.call('HGET', KEYS[4], 'endTime')
            local status = redis.call('HGET', KEYS[4], 'status')
            if status == 'FINISHED' or status == 'CANCELLED' then return 4 end
            if startTime and ARGV[1] < startTime then return 3 end
            if endTime and ARGV[1] > endTime then return 4 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 2 end
            local stock = tonumber(redis.call('GET', KEYS[1]))
            if stock == nil then return 5 end
            if stock <= 0 then return 1 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
            redis.call('SET', KEYS[3], ARGV[3], 'EX', ARGV[4])
            return 0
            """;
    private static final String ROLLBACK_PROMOTION_LUA = """
            if redis.call('EXISTS', KEYS[2]) == 1 then
                redis.call('INCR', KEYS[1])
                redis.call('DEL', KEYS[2])
            end
            redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[2])
            return 1
            """;

    private final WaitlistClaimService claimService;
    private final SeckillActivityRepository activityRepository;
    private final StringRedisTemplate redisTemplate;
    private final SeckillAdmissionService admissionService;
    private final ReliableOrderPublisher reliableOrderPublisher;
    private final ObjectMapper objectMapper;
    private final long userFlagTtlSeconds;
    private final long resultTtlSeconds;

    public WaitlistPromotionService(
            WaitlistClaimService claimService,
            SeckillActivityRepository activityRepository,
            StringRedisTemplate redisTemplate,
            SeckillAdmissionService admissionService,
            ReliableOrderPublisher reliableOrderPublisher,
            ObjectMapper objectMapper,
            @Value("${app.seckill.user-flag-ttl-seconds}") long userFlagTtlSeconds,
            @Value("${app.seckill.result-ttl-seconds}") long resultTtlSeconds
    ) {
        this.claimService = claimService;
        this.activityRepository = activityRepository;
        this.redisTemplate = redisTemplate;
        this.admissionService = admissionService;
        this.reliableOrderPublisher = reliableOrderPublisher;
        this.objectMapper = objectMapper;
        this.userFlagTtlSeconds = userFlagTtlSeconds;
        this.resultTtlSeconds = resultTtlSeconds;
    }

    public boolean promoteOne(Long activityId) {
        SeckillWaitlistEntry entry = claimService.claimNext(activityId);
        if (entry == null) {
            return false;
        }
        SeckillActivity activity = activityRepository.findById(activityId).orElse(null);
        if (activity == null) {
            claimService.updateStatus(entry.getId(), WaitlistStatus.CANCELLED);
            return false;
        }
        String queueing = resultJson(activityId, "候补资格已递补，订单排队中");
        Long result;
        try {
            result = redisTemplate.execute(
                    new DefaultRedisScript<>(PROMOTE_LUA, Long.class),
                    List.of(
                            SeckillRedisKeys.stock(activityId),
                            SeckillRedisKeys.user(activityId, entry.getUserId()),
                            SeckillRedisKeys.result(activityId, entry.getUserId()),
                            SeckillRedisKeys.activity(activityId)
                    ),
                    LocalDateTime.now().toString(),
                    String.valueOf(userFlagTtlSeconds),
                    queueing,
                    String.valueOf(resultTtlSeconds)
            );
        } catch (RuntimeException ex) {
            claimService.updateStatus(entry.getId(), WaitlistStatus.WAITING);
            return false;
        }
        if (result == null || result == 1L || result == 3L || result == 5L) {
            claimService.updateStatus(entry.getId(), WaitlistStatus.WAITING);
            return false;
        }
        if (result == 2L || result == 4L) {
            claimService.updateStatus(entry.getId(), WaitlistStatus.CANCELLED);
            return false;
        }

        OrderCreateMessage message = new OrderCreateMessage(
                UUID.randomUUID().toString(),
                EVENT_TYPE,
                activityId,
                entry.getUserId(),
                activity.getProductId(),
                entry.getQuantity(),
                activity.getSeckillPrice(),
                generateOrderNo(),
                LocalDateTime.now(),
                UUID.randomUUID().toString()
        );
        try {
            SeckillPublishEvent event = admissionService.persistAdmission(message);
            reliableOrderPublisher.publishNow(event, message);
            claimService.updateStatus(entry.getId(), WaitlistStatus.PROMOTED);
            return true;
        } catch (RuntimeException ex) {
            rollbackPromotion(entry);
            claimService.updateStatus(entry.getId(), WaitlistStatus.WAITING);
            return false;
        }
    }

    private void rollbackPromotion(SeckillWaitlistEntry entry) {
        redisTemplate.execute(
                new DefaultRedisScript<>(ROLLBACK_PROMOTION_LUA, Long.class),
                List.of(
                        SeckillRedisKeys.stock(entry.getActivityId()),
                        SeckillRedisKeys.user(entry.getActivityId(), entry.getUserId()),
                        SeckillRedisKeys.result(entry.getActivityId(), entry.getUserId())
                ),
                resultJson(entry.getActivityId(), "候补排队中"),
                String.valueOf(resultTtlSeconds)
        );
    }

    private String resultJson(Long activityId, String message) {
        try {
            return objectMapper.writeValueAsString(
                    new SeckillResultResponse(activityId, "QUEUEING", null, null, message)
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize waitlist result", ex);
        }
    }

    private String generateOrderNo() {
        return "ORD"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"))
                + ThreadLocalRandom.current().nextInt(1000, 10000);
    }
}
