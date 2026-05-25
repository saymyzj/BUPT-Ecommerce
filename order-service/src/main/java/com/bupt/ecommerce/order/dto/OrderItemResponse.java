package com.bupt.ecommerce.order.dto;

import com.bupt.ecommerce.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long itemId,
        Long productId,
        Integer quantity,
        BigDecimal unitPrice
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(), item.getQuantity(), item.getUnitPrice());
    }
}
