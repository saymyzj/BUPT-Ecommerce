package com.bupt.ecommerce.user.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.security.Role;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ApiResponse.success(Map.of("userId", 10001L));
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest request) {
        return ApiResponse.success(Map.of(
                "token", "jwt-token",
                "user", Map.of("userId", 10001L, "username", request.username(), "role", Role.CUSTOMER.name())
        ));
    }

    @PostMapping("/admin/login")
    public ApiResponse<Map<String, Object>> adminLogin(@RequestBody LoginRequest request) {
        return ApiResponse.success(Map.of(
                "token", "admin-jwt-token",
                "user", Map.of("userId", 1L, "username", request.username(), "role", Role.ADMIN.name())
        ));
    }

    @PostMapping("/logout")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.success(true);
    }

    public record RegisterRequest(@NotBlank String username, @NotBlank String password, String phone) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
