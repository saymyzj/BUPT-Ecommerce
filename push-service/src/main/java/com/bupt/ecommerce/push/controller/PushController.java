package com.bupt.ecommerce.push.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/push/orders")
@Tag(name = "06 实时推送", description = "订单创建结果推送连接")
public class PushController {

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "建立推送连接", description = "CUSTOMER 接口，建立 SSE 推送连接")
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(ApiResponse.success(Map.of("status", "CONNECTED"))));
        } catch (IOException ignored) {
        }
        return emitter;
    }
}
