package com.bupt.ecommerce.order.entity;

public enum OrderStatus {
    QUEUEING,
    PENDING_PAYMENT,
    CREATED,
    FAILED,
    CANCELLED,
    PAID
}
