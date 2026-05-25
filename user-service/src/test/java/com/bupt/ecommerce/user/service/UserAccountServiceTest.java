package com.bupt.ecommerce.user.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAccountServiceTest {

    private final UserAccountService service = new UserAccountService("test-admin", "test-password");

    @Test
    void defaultAdminShouldExistAndLogin() {
        UserAccountService.UserAccount account = service.loginAsAdmin("test-admin", "test-password");
        assertEquals(1L, account.userId());
        assertEquals(Role.ADMIN, account.role());
    }

    @Test
    void unknownAdminShouldNotBeAutoCreated() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loginAsAdmin("new-admin", "123456"));
        assertEquals(ErrorCode.NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void unknownCustomerShouldNotBeAutoCreated() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loginAsCustomer("new-user", "123456"));
        assertEquals(ErrorCode.NOT_FOUND.code(), ex.getCode());
    }
}
