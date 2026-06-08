package com.bupt.ecommerce.gateway.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class GatewayInfoController {

    @GetMapping("/")
    public ApiResponse<Map<String, Object>> index() {
        return ApiResponse.success(Map.of(
                "service", "gateway-service",
                "status", "UP",
                "swagger", "http://localhost:8080/swagger-ui.html",
                "testPage", "simple-test-page/index.html",
                "routes", List.of(
                        "/api/auth/**",
                        "/api/users/**",
                        "/api/products/**",
                        "/api/seckill/**",
                        "/api/orders/**",
                        "/api/ai/**",
                        "/api/push/**"
                )
        ));
    }
}
