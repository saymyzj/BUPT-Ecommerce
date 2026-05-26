package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.CreateSeckillActivityRequest;
import com.bupt.ecommerce.product.dto.SeckillActivityResponse;
import com.bupt.ecommerce.product.dto.SeckillQueuedResponse;
import com.bupt.ecommerce.product.dto.SeckillRequest;
import com.bupt.ecommerce.product.dto.SeckillResultResponse;
import com.bupt.ecommerce.product.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill/activities")
@Tag(name = "03 秒杀活动", description = "秒杀活动创建、详情、下单和结果查询")
public class SeckillController {

    private final SeckillService seckillService;
    private final Long defaultUserId;
    private final boolean enableDefaultUser;

    public SeckillController(
            SeckillService seckillService,
            @Value("${app.local-demo.default-user-id}") Long defaultUserId,
            @Value("${app.local-demo.enable-default-user:true}") boolean enableDefaultUser
    ) {
        this.seckillService = seckillService;
        this.defaultUserId = defaultUserId;
        this.enableDefaultUser = enableDefaultUser;
    }

    @PostMapping
    @Operation(summary = "创建秒杀活动", description = "ADMIN 接口，创建秒杀活动")
    public ApiResponse<SeckillActivityResponse> create(@Valid @RequestBody CreateSeckillActivityRequest request) {
        return ApiResponse.success(seckillService.createActivity(request));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "秒杀活动详情", description = "PUBLIC 接口，查询秒杀活动详情")
    public ApiResponse<SeckillActivityResponse> detail(@PathVariable("activityId") Long activityId) {
        return ApiResponse.success(seckillService.detail(activityId));
    }

    @PostMapping("/{activityId}/orders")
    @Operation(summary = "发起秒杀", description = "CUSTOMER 接口，发起秒杀并返回排队中")
    public ApiResponse<SeckillQueuedResponse> seckill(
            @PathVariable("activityId") Long activityId,
            @Valid @RequestBody SeckillRequest request,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.queued(seckillService.seckill(activityId, resolveUserId(userId), request));
    }

    @GetMapping("/{activityId}/result")
    @Operation(summary = "查询秒杀结果", description = "CUSTOMER 接口，查询当前用户秒杀结果")
    public ApiResponse<SeckillResultResponse> result(
            @PathVariable("activityId") Long activityId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.success(seckillService.result(activityId, resolveUserId(userId)));
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
}
