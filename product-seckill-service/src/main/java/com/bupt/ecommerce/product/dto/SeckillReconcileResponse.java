package com.bupt.ecommerce.product.dto;

public record SeckillReconcileResponse(
        Long activityId,
        int initialStock,
        long createdOrders,
        long activeReservations,
        int redisStock
) {
}
