package com.bupt.ecommerce.order.redis;

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

    public static String compensated(String requestId) {
        return "seckill:compensated:%s".formatted(requestId);
    }
}
