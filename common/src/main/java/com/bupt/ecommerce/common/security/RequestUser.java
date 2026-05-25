package com.bupt.ecommerce.common.security;

public record RequestUser(Long userId, String username, Role role) {
}
