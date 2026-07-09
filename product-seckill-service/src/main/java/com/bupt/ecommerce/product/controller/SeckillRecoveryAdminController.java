package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.product.dto.PublishEventResponse;
import com.bupt.ecommerce.product.service.ReliableOrderPublisher;
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
@RequestMapping("/api/seckill/admin/publish-events")
@Tag(name = "03 秒杀活动", description = "秒杀活动与可靠投递恢复")
public class SeckillRecoveryAdminController {

    private final ReliableOrderPublisher reliableOrderPublisher;
    private final boolean enableDefaultUser;

    public SeckillRecoveryAdminController(
            ReliableOrderPublisher reliableOrderPublisher,
            @Value("${app.local-demo.enable-default-user:false}") boolean enableDefaultUser
    ) {
        this.reliableOrderPublisher = reliableOrderPublisher;
        this.enableDefaultUser = enableDefaultUser;
    }

    @GetMapping
    @Operation(summary = "查询秒杀投递事件", description = "ADMIN 接口，查看可靠投递状态")
    public ApiResponse<PageResponse<PublishEventResponse>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(reliableOrderPublisher.page(page, pageSize));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "重试秒杀投递事件", description = "ADMIN 接口，使用原 messageId 幂等重试")
    public ApiResponse<PublishEventResponse> retry(
            @PathVariable("id") Long id,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(reliableOrderPublisher.retryEvent(id));
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
