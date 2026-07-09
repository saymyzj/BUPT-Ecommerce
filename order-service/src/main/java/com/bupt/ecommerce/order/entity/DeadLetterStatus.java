package com.bupt.ecommerce.order.entity;

public enum DeadLetterStatus {
    RECEIVED,
    REPLAYED,
    RESOLVED,
    IGNORED
}
