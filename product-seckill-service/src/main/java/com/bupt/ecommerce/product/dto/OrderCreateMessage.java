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
        LocalDateTime createdAt,
        String requestId
) {
    public OrderCreateMessage(
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
        this(messageId, eventType, activityId, userId, productId, quantity, seckillPrice, orderNo, createdAt, messageId);
    }
}
