package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/seckill/activities")
public class SeckillController {

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of("activityId", 1L, "status", "READY"));
    }

    @GetMapping("/{activityId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long activityId) {
        return ApiResponse.success(Map.of("activityId", activityId, "status", "ONGOING"));
    }

    @PostMapping("/{activityId}/orders")
    public ApiResponse<Map<String, Object>> seckill(@PathVariable Long activityId, @RequestBody Map<String, Object> request) {
        return ApiResponse.queued(Map.of("activityId", activityId, "status", "QUEUEING"));
    }

    @GetMapping("/{activityId}/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable Long activityId) {
        return ApiResponse.success(Map.of("activityId", activityId, "status", "QUEUEING"));
    }
}
