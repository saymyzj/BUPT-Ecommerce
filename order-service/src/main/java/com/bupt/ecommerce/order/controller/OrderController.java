package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.order.dto.OrderResponse;
import com.bupt.ecommerce.order.dto.SeckillResultResponse;
import com.bupt.ecommerce.order.dto.SeckillAccountingResponse;
import com.bupt.ecommerce.order.service.OrderService;
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
@RequestMapping("/api/orders")
@Tag(name = "04 订单查询", description = "用户订单查询和管理员订单查询")
public class OrderController {

    private final OrderService orderService;
    private final Long defaultUserId;
    private final boolean enableDefaultUser;
    private final String internalToken;

    public OrderController(
            OrderService orderService,
            @Value("${app.local-demo.default-user-id}") Long defaultUserId,
            @Value("${app.local-demo.enable-default-user:false}") boolean enableDefaultUser,
            @Value("${app.internal.token}") String internalToken
    ) {
        this.orderService = orderService;
        this.defaultUserId = defaultUserId;
        this.enableDefaultUser = enableDefaultUser;
        this.internalToken = internalToken;
    }

    @GetMapping
    @Operation(summary = "订单列表", description = "CUSTOMER 接口，分页查询当前用户订单")
    public ApiResponse<PageResponse<OrderResponse>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId
    ) {
        return ApiResponse.success(orderService.page(resolveUserId(userId), page, pageSize));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单详情", description = "CUSTOMER 接口，查询订单详情")
    public ApiResponse<OrderResponse> detail(
            @PathVariable("orderId") Long orderId,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId
    ) {
        return ApiResponse.success(orderService.detail(orderId, resolveUserId(userId)));
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "支付订单", description = "CUSTOMER 接口，演示订单从待支付变为已支付")
    public ApiResponse<OrderResponse> pay(
            @PathVariable("orderId") Long orderId,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId
    ) {
        return ApiResponse.success(orderService.pay(orderId, resolveUserId(userId)));
    }

    @GetMapping("/admin")
    @Operation(summary = "管理员订单查询", description = "ADMIN 接口，分页查询订单")
    public ApiResponse<PageResponse<OrderResponse>> adminPage(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "status", required = false) String status,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(orderService.adminPage(page, pageSize, status));
    }

    @GetMapping("/internal/seckill-result")
    @Operation(summary = "内部查询秒杀订单结果", description = "服务间接口，按活动和用户回查订单结果")
    public ApiResponse<SeckillResultResponse> seckillResult(
            @RequestParam("activityId") Long activityId,
            @RequestParam("userId") Long userId,
            @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false) String token
    ) {
        requireInternalToken(token);
        return ApiResponse.success(orderService.findSeckillResult(activityId, userId));
    }

    @GetMapping("/internal/seckill-accounting")
    @Operation(summary = "内部查询秒杀对账数据", description = "服务间接口，返回活动已创建订单数")
    public ApiResponse<SeckillAccountingResponse> seckillAccounting(
            @RequestParam("activityId") Long activityId,
            @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false) String token
    ) {
        requireInternalToken(token);
        return ApiResponse.success(orderService.accounting(activityId));
    }

    private Long resolveUserId(Long userId) {
        if (userId != null) {
            return userId;
        }
        if (enableDefaultUser) {
            return defaultUserId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    private void requireAdmin(String role) {
        if (enableDefaultUser && role == null) {
            return;
        }
        if (Role.ADMIN.name().equals(role)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private void requireInternalToken(String token) {
        if (internalToken.equals(token)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
