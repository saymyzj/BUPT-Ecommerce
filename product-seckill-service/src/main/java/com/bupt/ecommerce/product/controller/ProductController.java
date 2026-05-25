package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @GetMapping
    public ApiResponse<PageResponse<Map<String, Object>>> page() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }

    @GetMapping("/{productId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long productId) {
        return ApiResponse.success(Map.of("productId", productId, "name", "demo product", "price", 99.9));
    }

    @PutMapping("/{productId}/stock")
    public ApiResponse<Map<String, Object>> stock(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("productId", productId, "stock", request.getOrDefault("stock", 0)));
    }

    @org.springframework.web.bind.annotation.PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("productId", 20001L, "name", request.get("name")));
    }
}
