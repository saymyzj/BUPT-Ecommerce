package com.bupt.ecommerce.gateway.filter;

import com.bupt.ecommerce.common.security.Role;
import org.springframework.http.HttpMethod;

public class GatewayAuthPolicy {

    public boolean isGatewayBlockedEndpoint(String path) {
        return path.startsWith("/api/orders/internal/")
                || path.matches("^/api/push/[^/]+/internal/.*$");
    }

    public boolean isPublicEndpoint(HttpMethod method, String path) {
        if (path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars/")
                || "/".equals(path)
                || "/favicon.ico".equals(path)
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/openapi/")) {
            return true;
        }
        if (HttpMethod.POST.equals(method)
                && ("/api/auth/register".equals(path)
                || "/api/auth/login".equals(path)
                || "/api/auth/admin/login".equals(path))) {
            return true;
        }
        if (HttpMethod.GET.equals(method)
                && ("/api/products".equals(path) || path.matches("^/api/products/[^/]+$"))) {
            return true;
        }
        return HttpMethod.GET.equals(method)
                && path.matches("^/api/seckill/activities/[^/]+$");
    }

    public Role requiredRole(HttpMethod method, String path) {
        if (path.startsWith("/api/orders/admin")
                || path.startsWith("/api/seckill/admin")
                || (HttpMethod.POST.equals(method) && "/api/products".equals(path))
                || (HttpMethod.PUT.equals(method) && path.matches("^/api/products/[^/]+/stock$"))
                || (HttpMethod.PUT.equals(method) && path.matches("^/api/products/[^/]+/offline$"))
                || (HttpMethod.POST.equals(method) && "/api/seckill/activities".equals(path))) {
            return Role.ADMIN;
        }
        if (HttpMethod.POST.equals(method)
                && path.matches("^/api/seckill/activities/[^/]+/reconcile$")) {
            return Role.ADMIN;
        }
        return Role.CUSTOMER;
    }
}
