package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> page() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long orderId) {
        return ApiResponse.success(Map.of("orderId", orderId, "status", "CREATED"));
    }

    @GetMapping("/admin")
    public ApiResponse<PageResponse<Map<String, Object>>> adminPage() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }
}
