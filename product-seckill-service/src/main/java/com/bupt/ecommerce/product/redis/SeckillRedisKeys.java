package com.bupt.ecommerce.product.redis;

public final class SeckillRedisKeys {

    private SeckillRedisKeys() {
    }

    public static String stock(Long activityId) {
        return "seckill:stock:%d".formatted(activityId);
    }

    public static String user(Long activityId, Long userId) {
        return "seckill:user:%d:%d".formatted(activityId, userId);
    }

    public static String result(Long activityId, Long userId) {
        return "seckill:result:%d:%d".formatted(activityId, userId);
    }

    public static String activity(Long activityId) {
        return "seckill:activity:%d".formatted(activityId);
    }
}
