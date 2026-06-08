package com.bupt.ecommerce.user.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.user.entity.UserAccountEntity;
import com.bupt.ecommerce.user.entity.UserStatus;
import com.bupt.ecommerce.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccountServiceTest {

    private UserAccountRepository repository;
    private UserAccountService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        service = new UserAccountService(repository, "test-admin", "test-password");
    }

    @Test
    void defaultAdminShouldBeInitializedWhenMissing() {
        when(repository.findByUsername("test-admin")).thenReturn(Optional.empty());
        when(repository.save(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        service.initializeAdmin();

        when(repository.findByUsername("test-admin")).thenReturn(Optional.of(adminAccount()));
        UserAccountService.UserAccount account = service.loginAsAdmin("test-admin", "test-password");
        assertEquals(1L, account.userId());
        assertEquals(Role.ADMIN, account.role());
    }

    @Test
    void unknownAdminShouldNotBeAutoCreatedOnLogin() {
        when(repository.findByUsername("new-admin")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loginAsAdmin("new-admin", "123456"));
        assertEquals(ErrorCode.NOT_FOUND.code(), ex.getCode());
    }

    @Test
    void unknownCustomerShouldNotBeAutoCreated() {
        when(repository.findByUsername("new-user")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.loginAsCustomer("new-user", "123456"));
        assertEquals(ErrorCode.NOT_FOUND.code(), ex.getCode());
    }

    private UserAccountEntity adminAccount() {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(1L);
        entity.setUsername("test-admin");
        entity.setPasswordHash("c638833f69bbfb3c267afa0a74434812436b8f08a81fd263c6be6871de4f1265");
        entity.setRole(Role.ADMIN);
        entity.setStatus(UserStatus.ACTIVE);
        return entity;
    }
}
