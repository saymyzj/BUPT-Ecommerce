package com.bupt.ecommerce.order.dto;

public record OrderPushEvent(
        String eventType,
        Long activityId,
        Long orderId,
        String orderNo,
        Long userId,
        String status,
        String message
) {
}
