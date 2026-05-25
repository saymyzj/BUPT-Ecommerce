package com.bupt.ecommerce.ai.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/products")
@Tag(name = "05 AI 智能导购", description = "商品 AI 咨询")
public class AiController {

    @PostMapping("/{productId}/consult")
    @Operation(summary = "商品咨询", description = "CUSTOMER / ADMIN 接口，返回商品咨询回答")
    public ApiResponse<Map<String, Object>> consult(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of(
                "answer", "这是一个占位回答，后续接入 LLM。"
        ));
    }
}
