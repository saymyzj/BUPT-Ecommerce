package com.bupt.ecommerce.order.redis;

public final class SeckillRedisKeys {

    private SeckillRedisKeys() {
    }

    public static String result(Long activityId, Long userId) {
        return "seckill:result:%d:%d".formatted(activityId, userId);
    }
}
