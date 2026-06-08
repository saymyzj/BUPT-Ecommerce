package com.bupt.ecommerce.push.controller;

import com.bupt.ecommerce.common.exception.GlobalExceptionHandler;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.push.dto.OrderPushEvent;
import com.bupt.ecommerce.push.service.PushConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushControllerTest {

    private final PushConnectionService service = new PushConnectionService();
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new PushController(service, "internal-token"))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publishShouldRejectMissingInternalToken() throws Exception {
        mockMvc.perform(post("/api/push/orders/internal/events")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(event())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void publishShouldAcceptValidInternalToken() throws Exception {
        mockMvc.perform(post("/api/push/orders/internal/events")
                        .header(AuthHeaders.INTERNAL_TOKEN, "internal-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(event())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.delivered").value(0));
    }

    private OrderPushEvent event() {
        return new OrderPushEvent(
                "ORDER_CREATED",
                1L,
                10L,
                "SO202606080001",
                10001L,
                "CREATED",
                "order created"
        );
    }
}
