package com.bupt.ecommerce.ai.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai/products")
public class AiController {

    @PostMapping("/{productId}/consult")
    public ApiResponse<Map<String, Object>> consult(@PathVariable Long productId, @RequestBody Map<String, Object> request) {
        return ApiResponse.success(Map.of(
                "answer", "这是一个占位回答，后续接入 LLM。",
                "productId", productId,
                "question", request.get("question")
        ));
    }
}
