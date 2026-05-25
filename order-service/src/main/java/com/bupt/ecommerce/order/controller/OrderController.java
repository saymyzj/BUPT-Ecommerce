package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.order.dto.OrderResponse;
import com.bupt.ecommerce.order.dto.SeckillResultResponse;
import com.bupt.ecommerce.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "04 订单查询", description = "用户订单查询和管理员订单查询")
public class OrderController {

    private final OrderService orderService;
    private final Long defaultUserId;

    public OrderController(OrderService orderService, @Value("${app.local-demo.default-user-id}") Long defaultUserId) {
        this.orderService = orderService;
        this.defaultUserId = defaultUserId;
    }

    @GetMapping
    @Operation(summary = "订单列表", description = "CUSTOMER 接口，分页查询当前用户订单")
    public ApiResponse<PageResponse<OrderResponse>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.success(orderService.page(userId == null ? defaultUserId : userId, page, pageSize));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "CUSTOMER 接口，查询订单详情")
    public ApiResponse<OrderResponse> detail(
            @PathVariable Long orderId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.success(orderService.detail(orderId, userId == null ? defaultUserId : userId));
    }

    @GetMapping("/admin")
    @Operation(summary = "管理员订单查询", description = "ADMIN 接口，分页查询订单")
    public ApiResponse<PageResponse<OrderResponse>> adminPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.success(orderService.adminPage(page, pageSize, status));
    }

    @GetMapping("/internal/seckill-result")
    @Operation(summary = "内部查询秒杀订单结果", description = "服务间接口，按活动和用户回查订单结果")
    public ApiResponse<SeckillResultResponse> seckillResult(
            @RequestParam Long activityId,
            @RequestParam Long userId
    ) {
        return ApiResponse.success(orderService.findSeckillResult(activityId, userId));
    }
}
