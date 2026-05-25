package com.bupt.ecommerce.order.dto;

public record SeckillResultResponse(
        Long activityId,
        String status,
        Long orderId,
        String orderNo,
        String message
) {
}
