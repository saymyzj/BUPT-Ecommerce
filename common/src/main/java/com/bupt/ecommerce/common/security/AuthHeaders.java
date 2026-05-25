package com.bupt.ecommerce.common.security;

public final class AuthHeaders {

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String USER_ID = "X-User-Id";
    public static final String USERNAME = "X-Username";
    public static final String ROLE = "X-User-Role";

    private AuthHeaders() {
    }
}
