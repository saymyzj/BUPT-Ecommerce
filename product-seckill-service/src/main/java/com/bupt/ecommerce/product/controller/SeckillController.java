package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill/activities")
@Tag(name = "03 秒杀活动", description = "秒杀活动创建、详情、下单和结果查询")
public class SeckillController {

    @PostMapping
    @Operation(summary = "创建秒杀活动", description = "ADMIN 接口，创建秒杀活动")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("activityId", 1L, "status", "READY"));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "秒杀活动详情", description = "PUBLIC 接口，查询秒杀活动详情")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long activityId) {
        return ApiResponse.success(Map.of("activityId", activityId, "status", "ONGOING"));
    }

    @PostMapping("/{activityId}/orders")
    @Operation(summary = "发起秒杀", description = "CUSTOMER 接口，发起秒杀并返回排队中")
    public ApiResponse<Map<String, Object>> seckill(@PathVariable Long activityId, @RequestBody Map<String, Object> request) {
        return ApiResponse.queued(Map.of("activityId", activityId, "status", "QUEUEING"));
    }

    @GetMapping("/{activityId}/result")
    @Operation(summary = "查询秒杀结果", description = "CUSTOMER 接口，查询当前用户秒杀结果")
    public ApiResponse<Map<String, Object>> result(@PathVariable Long activityId) {
        return ApiResponse.success(Map.of("activityId", activityId, "status", "QUEUEING"));
    }
}
