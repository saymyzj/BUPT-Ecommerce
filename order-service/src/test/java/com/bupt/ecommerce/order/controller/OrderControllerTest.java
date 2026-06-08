package com.bupt.ecommerce.order.controller;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.order.service.OrderService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OrderControllerTest {

    private final OrderController controller = new OrderController(mock(OrderService.class), 10001L, false, "internal-token");

    @Test
    void adminPageShouldRejectNonAdminRoleInsideService() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.adminPage(1, 10, null, "CUSTOMER"));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
    }

    @Test
    void internalSeckillResultShouldRejectWrongInternalToken() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.seckillResult(1L, 10001L, "wrong-token"));

        assertEquals(ErrorCode.FORBIDDEN.code(), ex.getCode());
    }
}
