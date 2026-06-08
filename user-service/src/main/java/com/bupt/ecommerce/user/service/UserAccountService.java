package com.bupt.ecommerce.user.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.user.entity.UserAccountEntity;
import com.bupt.ecommerce.user.entity.UserStatus;
import com.bupt.ecommerce.user.repository.UserAccountRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final String adminUsername;
    private final String adminPassword;

    public UserAccountService(UserAccountRepository userAccountRepository,
                              @Value("${app.admin.username}") String adminUsername,
                              @Value("${app.admin.password}") String adminPassword) {
        this.userAccountRepository = userAccountRepository;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostConstruct
    @Transactional
    public void initializeAdmin() {
        userAccountRepository.findByUsername(adminUsername).orElseGet(() -> {
            UserAccountEntity admin = new UserAccountEntity();
            admin.setUsername(adminUsername);
            admin.setPasswordHash(hashPassword(adminPassword));
            admin.setRole(Role.ADMIN);
            admin.setStatus(UserStatus.ACTIVE);
            return userAccountRepository.save(admin);
        });
    }

    @Transactional
    public UserAccount register(String username, String password, String phone) {
        if (userAccountRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.CONFLICT.code(), "username already exists");
        }
        if (phone != null && !phone.isBlank() && userAccountRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.CONFLICT.code(), "phone already exists");
        }

        UserAccountEntity account = new UserAccountEntity();
        account.setUsername(username);
        account.setPasswordHash(hashPassword(password));
        account.setPhone(phone == null || phone.isBlank() ? null : phone);
        account.setRole(Role.CUSTOMER);
        account.setStatus(UserStatus.ACTIVE);
        return toAccount(userAccountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public UserAccount loginAsCustomer(String username, String password) {
        UserAccountEntity existing = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.code(), "user not found"));
        return verifyPassword(existing, password, Role.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public UserAccount loginAsAdmin(String username, String password) {
        UserAccountEntity existing = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND.code(), "admin account not found"));
        return verifyPassword(existing, password, Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findByUsername(String username) {
        return userAccountRepository.findByUsername(username).map(this::toAccount);
    }

    private UserAccount verifyPassword(UserAccountEntity account, String password, Role expectedRole) {
        if (!account.getPasswordHash().equals(hashPassword(password))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
        }
        if (account.getStatus() != UserStatus.ACTIVE || account.getRole() != expectedRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message());
        }
        return toAccount(account);
    }

    private UserAccount toAccount(UserAccountEntity entity) {
        return new UserAccount(
                entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getPhone(),
                entity.getRole()
        );
    }

    private String hashPassword(String password) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("failed to hash password", ex);
        }
    }

    public record UserAccount(Long userId, String username, String passwordHash, String phone, Role role) {
    }
}
