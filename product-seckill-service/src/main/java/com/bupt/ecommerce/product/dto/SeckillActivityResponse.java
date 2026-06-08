package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.SeckillActivity;
import com.bupt.ecommerce.product.entity.SeckillActivityStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SeckillActivityResponse(
        Long activityId,
        Long productId,
        String activityName,
        BigDecimal seckillPrice,
        Integer seckillStock,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String status
) {

    public static SeckillActivityResponse from(SeckillActivity activity) {
        SeckillActivityStatus displayStatus = resolveDisplayStatus(activity, LocalDateTime.now());
        return new SeckillActivityResponse(
                activity.getId(),
                activity.getProductId(),
                activity.getActivityName(),
                activity.getSeckillPrice(),
                activity.getSeckillStock(),
                activity.getStartTime(),
                activity.getEndTime(),
                displayStatus.name()
        );
    }

    private static SeckillActivityStatus resolveDisplayStatus(SeckillActivity activity, LocalDateTime now) {
        SeckillActivityStatus status = activity.getStatus();
        if (status != SeckillActivityStatus.READY) {
            return status;
        }
        if (!now.isBefore(activity.getStartTime()) && now.isBefore(activity.getEndTime())) {
            return SeckillActivityStatus.ONGOING;
        }
        if (!now.isBefore(activity.getEndTime())) {
            return SeckillActivityStatus.FINISHED;
        }
        return status;
    }
}
