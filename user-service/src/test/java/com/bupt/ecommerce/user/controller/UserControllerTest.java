package com.bupt.ecommerce.user.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserControllerTest {

    private final UserController controller = new UserController();

    @Test
    void meShouldReturnGatewayInjectedUserContext() {
        ApiResponse<Map<String, Object>> response = controller.me(10001L, "alice", "CUSTOMER");

        assertEquals(0, response.code());
        assertEquals("success", response.message());
        assertEquals(10001L, response.data().get("userId"));
        assertEquals("alice", response.data().get("username"));
        assertEquals("CUSTOMER", response.data().get("role"));
    }

    @Test
    void meShouldRejectDirectAuthorizationFallback() {
        assertThrows(BusinessException.class, () -> controller.me(null, null, null));
    }
}
