package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "04 订单查询", description = "用户订单查询和管理员订单查询")
public class OrderController {

    @GetMapping
    @Operation(summary = "订单列表", description = "CUSTOMER 接口，分页查询当前用户订单")
    public ApiResponse<PageResponse<Map<String, Object>>> page() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "CUSTOMER 接口，查询订单详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long orderId) {
        return ApiResponse.success(Map.of("orderId", orderId, "status", "CREATED"));
    }

    @GetMapping("/admin")
    @Operation(summary = "管理员订单查询", description = "ADMIN 接口，分页查询订单")
    public ApiResponse<PageResponse<Map<String, Object>>> adminPage() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }
}
