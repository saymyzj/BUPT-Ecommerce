package com.bupt.ecommerce.push.dto;

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
