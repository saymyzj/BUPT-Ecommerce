package com.bupt.ecommerce.push.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.push.dto.OrderPushEvent;
import com.bupt.ecommerce.push.dto.PushPublishResult;
import com.bupt.ecommerce.push.service.PushConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/push/orders")
@Tag(name = "06 实时推送", description = "订单创建结果推送连接")
public class PushController {

    private final PushConnectionService pushConnectionService;
    private final String internalToken;

    public PushController(PushConnectionService pushConnectionService,
                          @Value("${app.internal.token}") String internalToken) {
        this.pushConnectionService = pushConnectionService;
        this.internalToken = internalToken;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立推送连接", description = "CUSTOMER 接口，建立 SSE 推送连接")
    public SseEmitter subscribe(@RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return pushConnectionService.subscribe(userId);
    }

    @PostMapping("/internal/events")
    @Operation(summary = "内部发布订单推送事件", description = "order-service 落库后调用，只向订单所属用户推送")
    public ApiResponse<PushPublishResult> publish(
            @RequestHeader(value = AuthHeaders.INTERNAL_TOKEN, required = false) String token,
            @RequestBody OrderPushEvent event
    ) {
        if (!internalToken.equals(token)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return ApiResponse.success(new PushPublishResult(pushConnectionService.publish(event)));
    }
}
