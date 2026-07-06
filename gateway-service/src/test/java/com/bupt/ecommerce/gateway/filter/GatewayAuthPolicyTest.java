package com.bupt.ecommerce.gateway.filter;

import com.bupt.ecommerce.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayAuthPolicyTest {

    private final GatewayAuthPolicy policy = new GatewayAuthPolicy();

    @Test
    void publicEndpointsAreAllowedWithoutToken() {
        assertTrue(policy.isPublicEndpoint(HttpMethod.POST, "/api/auth/login"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.POST, "/api/auth/register"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/api/products"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/api/products/10001"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/api/seckill/activities/1"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/swagger-ui/index.html"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/v3/api-docs"));
        assertTrue(policy.isPublicEndpoint(HttpMethod.GET, "/openapi/user"));
    }

    @Test
    void adminRoutesRequireAdminRole() {
        assertEquals(Role.ADMIN, policy.requiredRole(HttpMethod.POST, "/api/products"));
        assertEquals(Role.ADMIN, policy.requiredRole(HttpMethod.PUT, "/api/products/10001/stock"));
        assertEquals(Role.ADMIN, policy.requiredRole(HttpMethod.PUT, "/api/products/10001/offline"));
        assertEquals(Role.ADMIN, policy.requiredRole(HttpMethod.POST, "/api/seckill/activities"));
        assertEquals(Role.ADMIN, policy.requiredRole(HttpMethod.GET, "/api/orders/admin"));
    }

    @Test
    void internalOrderRoutesAreBlockedAtGateway() {
        assertTrue(policy.isGatewayBlockedEndpoint("/api/orders/internal/seckill-result"));
        assertFalse(policy.isGatewayBlockedEndpoint("/api/orders/1"));
    }

    @Test
    void internalPushRoutesAreBlockedAtGateway() {
        assertTrue(policy.isGatewayBlockedEndpoint("/api/push/orders/internal/events"));
        assertFalse(policy.isGatewayBlockedEndpoint("/api/push/orders/subscribe"));
    }

    @Test
    void customerRoutesRequireCustomerRoleByDefault() {
        assertEquals(Role.CUSTOMER, policy.requiredRole(HttpMethod.POST, "/api/seckill/activities/1/orders"));
        assertEquals(Role.CUSTOMER, policy.requiredRole(HttpMethod.GET, "/api/users/me"));
    }
}
