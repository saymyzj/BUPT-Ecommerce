package com.bupt.ecommerce.product.dto;

import com.bupt.ecommerce.product.entity.SeckillActivity;

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
        return new SeckillActivityResponse(
                activity.getId(),
                activity.getProductId(),
                activity.getActivityName(),
                activity.getSeckillPrice(),
                activity.getSeckillStock(),
                activity.getStartTime(),
                activity.getEndTime(),
                activity.getStatus().name()
        );
    }
}
