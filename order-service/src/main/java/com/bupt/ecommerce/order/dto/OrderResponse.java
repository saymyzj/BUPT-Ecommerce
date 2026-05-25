package com.bupt.ecommerce.order.dto;

import com.bupt.ecommerce.order.entity.Order;
import com.bupt.ecommerce.order.entity.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        String orderNo,
        Long userId,
        Long activityId,
        String status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<OrderItemResponse> items
) {

    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items.stream().map(OrderItemResponse::from).toList()
        );
    }

    public static OrderResponse summary(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getUserId(),
                order.getActivityId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                List.of()
        );
    }
}
