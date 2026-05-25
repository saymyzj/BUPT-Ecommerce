package com.bupt.ecommerce.user.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.security.JwtTokenUtil;
import com.bupt.ecommerce.user.service.UserAccountService;
import com.bupt.ecommerce.user.service.UserAccountService.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/auth")
@Tag(name = "01 认证与用户", description = "注册、登录、管理员登录和注销")
public class AuthController {

    private static final Duration TOKEN_TTL = Duration.ofHours(1);

    private final UserAccountService userAccountService;
    private final String jwtSecret;

    public AuthController(UserAccountService userAccountService,
                          @Value("${app.jwt.secret}") String jwtSecret) {
        this.userAccountService = userAccountService;
        this.jwtSecret = jwtSecret;
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "PUBLIC 接口，注册普通用户并返回 userId")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = userAccountService.register(request.username(), request.password(), request.phone());
        return ApiResponse.success(Map.of("userId", account.userId()));
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "PUBLIC 接口，登录成功后返回 CUSTOMER JWT")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        UserAccount account = userAccountService.loginAsCustomer(request.username(), request.password());
        return loginResponse(account);
    }

    @PostMapping("/admin/login")
    @Operation(summary = "管理员登录", description = "PUBLIC 接口，登录成功后返回 ADMIN JWT")
    public ApiResponse<Map<String, Object>> adminLogin(@Valid @RequestBody LoginRequest request) {
        UserAccount account = userAccountService.loginAsAdmin(request.username(), request.password());
        return loginResponse(account);
    }

    @PostMapping("/logout")
    @Operation(summary = "用户注销", description = "CUSTOMER / ADMIN 接口，当前基线返回注销成功")
    public ApiResponse<Boolean> logout() {
        return ApiResponse.success(true);
    }

    private ApiResponse<Map<String, Object>> loginResponse(UserAccount account) {
        String token = JwtTokenUtil.generateToken(
                account.userId(),
                account.username(),
                account.role(),
                TOKEN_TTL,
                jwtSecret
        );
        return ApiResponse.success(Map.of(
                "token", token,
                "user", Map.of(
                        "userId", account.userId(),
                        "username", account.username(),
                        "role", account.role().name()
                )
        ));
    }

    public record RegisterRequest(@NotBlank String username, @NotBlank String password, String phone) {}
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
