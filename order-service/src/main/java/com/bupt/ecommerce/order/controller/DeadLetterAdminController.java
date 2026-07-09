package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.order.dto.DeadLetterResponse;
import com.bupt.ecommerce.order.service.DeadLetterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders/admin/dead-letters")
@Tag(name = "04 订单查询", description = "订单查询与死信恢复")
public class DeadLetterAdminController {

    private final DeadLetterService deadLetterService;
    private final boolean enableDefaultUser;

    public DeadLetterAdminController(
            DeadLetterService deadLetterService,
            @Value("${app.local-demo.enable-default-user:false}") boolean enableDefaultUser
    ) {
        this.deadLetterService = deadLetterService;
        this.enableDefaultUser = enableDefaultUser;
    }

    @GetMapping
    @Operation(summary = "查询订单死信", description = "ADMIN 接口，分页查看订单死信")
    public ApiResponse<PageResponse<DeadLetterResponse>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(deadLetterService.page(page, pageSize));
    }

    @PostMapping("/{id}/replay")
    @Operation(summary = "重放订单死信", description = "ADMIN 接口，幂等重新投递订单死信")
    public ApiResponse<DeadLetterResponse> replay(
            @PathVariable("id") Long id,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(deadLetterService.replay(id));
    }

    private void requireAdmin(String role) {
        if (enableDefaultUser && role == null) {
            return;
        }
        if (!Role.ADMIN.name().equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
