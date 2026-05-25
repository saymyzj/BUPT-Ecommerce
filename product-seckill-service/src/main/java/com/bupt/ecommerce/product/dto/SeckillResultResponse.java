package com.bupt.ecommerce.product.dto;

public record SeckillResultResponse(
        Long activityId,
        String status,
        Long orderId,
        String orderNo,
        String message
) {
}
