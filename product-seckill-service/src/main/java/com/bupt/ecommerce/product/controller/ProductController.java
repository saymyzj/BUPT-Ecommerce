package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "02 商品与库存", description = "商品查询、创建和库存配置")
public class ProductController {

    @GetMapping
    @Operation(summary = "商品分页查询", description = "PUBLIC 接口，分页返回商品列表")
    public ApiResponse<PageResponse<Map<String, Object>>> page() {
        return ApiResponse.success(new PageResponse<>(List.of(), 1, 10, 0));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "商品详情", description = "PUBLIC 接口，按商品 ID 查询详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long productId) {
        return ApiResponse.success(Map.of("productId", productId, "name", "demo product", "price", 99.9));
    }

    @PutMapping("/{productId}/stock")
    @Operation(summary = "设置库存", description = "ADMIN 接口，设置指定商品库存")
    public ApiResponse<Map<String, Object>> stock(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("productId", productId, "stock", request.getOrDefault("stock", 0)));
    }

    @org.springframework.web.bind.annotation.PostMapping
    @Operation(summary = "创建商品", description = "ADMIN 接口，创建商品基础信息")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("productId", 20001L, "name", request.get("name")));
    }
}
