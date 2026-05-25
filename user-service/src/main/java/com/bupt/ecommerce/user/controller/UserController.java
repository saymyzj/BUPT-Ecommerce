package com.bupt.ecommerce.user.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.security.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.success(Map.of(
                "userId", 10001L,
                "username", "alice",
                "role", Role.CUSTOMER.name()
        ));
    }
}
