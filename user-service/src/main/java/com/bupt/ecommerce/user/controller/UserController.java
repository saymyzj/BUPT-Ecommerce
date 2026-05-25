package com.bupt.ecommerce.user.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.RequestUser;
import com.bupt.ecommerce.common.security.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@Tag(name = "01 认证与用户", description = "当前用户信息")
public class UserController {

    @GetMapping("/me")
    @Operation(summary = "当前用户信息", description = "CUSTOMER / ADMIN 接口，返回当前登录用户上下文")
    public ApiResponse<Map<String, Object>> me(
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = AuthHeaders.USERNAME, required = false) String username,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        if (userId == null || username == null || role == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
        }
        RequestUser requestUser;
        try {
            requestUser = new RequestUser(userId, username, Role.valueOf(role));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
        }
        return ApiResponse.success(Map.of(
                "userId", requestUser.userId(),
                "username", requestUser.username(),
                "role", requestUser.role().name()
        ));
    }
}
