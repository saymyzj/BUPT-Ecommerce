package com.bupt.ecommerce.ai.controller;

import com.bupt.ecommerce.ai.controller.AiController.ConsultRequest;
import com.bupt.ecommerce.ai.service.AiConsultService;
import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiControllerTest {

    @Test
    void consultShouldRejectMissingGatewayUserHeader() {
        AiController controller = new AiController(mock(AiConsultService.class));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.consult(20001L, new ConsultRequest("适合学生买吗"), null));

        assertEquals(ErrorCode.UNAUTHORIZED.code(), ex.getCode());
    }

    @Test
    void consultShouldAllowGatewayInjectedUserHeader() {
        AiConsultService aiConsultService = mock(AiConsultService.class);
        AiController controller = new AiController(aiConsultService);
        ApiResponse<Map<String, Object>> expected = ApiResponse.success(Map.of("answer", "可以"));
        when(aiConsultService.consult(20001L, "适合学生买吗")).thenReturn(expected);

        ApiResponse<Map<String, Object>> response =
                controller.consult(20001L, new ConsultRequest("适合学生买吗"), 10001L);

        assertEquals(expected, response);
    }
}
