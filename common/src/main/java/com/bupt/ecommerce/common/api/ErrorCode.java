package com.bupt.ecommerce.common.api;

public enum ErrorCode {
    SUCCESS(0, "success"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 Token 无效"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "业务冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),
    INTERNAL_ERROR(500, "服务器内部错误"),
    STOCK_NOT_ENOUGH(10001, "库存不足"),
    SECKILL_NOT_STARTED(10002, "秒杀活动未开始"),
    SECKILL_FINISHED(10003, "秒杀活动已结束"),
    SECKILL_DUPLICATED(10004, "用户已参与该活动"),
    MQ_PUBLISH_FAILED(20001, "MQ 投递失败"),
    AI_UNAVAILABLE(30001, "AI 服务暂不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
