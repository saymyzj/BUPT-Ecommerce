package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.SeckillResultResponse;
import com.bupt.ecommerce.product.entity.SeckillWaitlistEntry;
import com.bupt.ecommerce.product.entity.WaitlistStatus;
import com.bupt.ecommerce.product.redis.SeckillRedisKeys;
import com.bupt.ecommerce.product.repository.SeckillWaitlistRepository;
import com.bupt.ecommerce.product.repository.SeckillActivityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class WaitlistService {

    private final SeckillWaitlistRepository repository;
    private final SeckillActivityRepository activityRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxSize;
    private final long resultTtlSeconds;

    public WaitlistService(
            SeckillWaitlistRepository repository,
            SeckillActivityRepository activityRepository,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.seckill-waitlist.enabled:false}") boolean enabled,
            @Value("${app.seckill-waitlist.max-size-per-activity:200}") int maxSize,
            @Value("${app.seckill.result-ttl-seconds}") long resultTtlSeconds
    ) {
        this.repository = repository;
        this.activityRepository = activityRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxSize = maxSize;
        this.resultTtlSeconds = resultTtlSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Transactional(readOnly = true)
    public boolean isWaiting(Long activityId, Long userId) {
        if (!enabled) {
            return false;
        }
        return repository.findByActivityIdAndUserId(activityId, userId)
                .map(entry -> entry.getStatus() == WaitlistStatus.WAITING
                        || entry.getStatus() == WaitlistStatus.PROMOTING)
                .orElse(false);
    }

    @Transactional
    public void enqueue(Long activityId, Long userId, Integer quantity) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        activityRepository.findByIdForUpdate(activityId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        OptionalEntry existing = existing(activityId, userId);
        if (existing.present()) {
            writeWaitingResult(activityId, userId);
            return;
        }
        if (repository.countByActivityIdAndStatus(activityId, WaitlistStatus.WAITING) >= maxSize) {
            throw new BusinessException(ErrorCode.STOCK_NOT_ENOUGH);
        }
        LocalDateTime now = LocalDateTime.now();
        SeckillWaitlistEntry entry = new SeckillWaitlistEntry();
        entry.setActivityId(activityId);
        entry.setUserId(userId);
        entry.setQuantity(quantity);
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        try {
            repository.save(entry);
        } catch (DataIntegrityViolationException ex) {
            if (!existing(activityId, userId).present()) {
                throw ex;
            }
        }
        writeWaitingResult(activityId, userId);
    }

    private OptionalEntry existing(Long activityId, Long userId) {
        return new OptionalEntry(repository.findByActivityIdAndUserId(activityId, userId).isPresent());
    }

    private void writeWaitingResult(Long activityId, Long userId) {
        try {
            String value = objectMapper.writeValueAsString(
                    new SeckillResultResponse(activityId, "QUEUEING", null, null, "候补排队中")
            );
            redisTemplate.opsForValue().set(
                    SeckillRedisKeys.result(activityId, userId),
                    value,
                    Duration.ofSeconds(resultTtlSeconds)
            );
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private record OptionalEntry(boolean present) {
    }
}
