package com.bupt.ecommerce.product.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderCreateMessage(
        String messageId,
        String eventType,
        Long activityId,
        Long userId,
        Long productId,
        Integer quantity,
        BigDecimal seckillPrice,
        String orderNo,
        LocalDateTime createdAt
) {
}
