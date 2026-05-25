package com.bupt.ecommerce.user.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.Role;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class UserAccountService {

    private final AtomicLong userIdSequence = new AtomicLong(10000);
    private final ConcurrentMap<String, UserAccount> usersByUsername = new ConcurrentHashMap<>();

    public UserAccountService(@Value("${app.admin.username}") String adminUsername,
                              @Value("${app.admin.password}") String adminPassword) {
        usersByUsername.put(adminUsername, new UserAccount(
                1L,
                adminUsername,
                hashPassword(adminPassword),
                null,
                Role.ADMIN
        ));
    }

    public UserAccount register(String username, String password, String phone) {
        UserAccount account = new UserAccount(
                userIdSequence.incrementAndGet(),
                username,
                hashPassword(password),
                phone,
                Role.CUSTOMER
        );
        UserAccount existing = usersByUsername.putIfAbsent(username, account);
        if (existing != null) {
            throw new BusinessException(ErrorCode.CONFLICT.code(), "用户名已存在");
        }
        return account;
    }

    public UserAccount loginAsCustomer(String username, String password) {
        UserAccount existing = usersByUsername.get(username);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.code(), "用户不存在");
        }
        return verifyPassword(existing, password, Role.CUSTOMER);
    }

    public UserAccount loginAsAdmin(String username, String password) {
        UserAccount existing = usersByUsername.get(username);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND.code(), "管理员账号不存在");
        }
        return verifyPassword(existing, password, Role.ADMIN);
    }

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    private UserAccount verifyPassword(UserAccount account, String password, Role expectedRole) {
        if (!account.passwordHash().equals(hashPassword(password))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message());
        }
        if (account.role() != expectedRole) {
            throw new BusinessException(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message());
        }
        return account;
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
