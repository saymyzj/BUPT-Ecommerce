package com.bupt.ecommerce.gateway.filter;

import com.bupt.ecommerce.common.security.Role;
import org.springframework.http.HttpMethod;

public class GatewayAuthPolicy {

    public boolean isPublicEndpoint(HttpMethod method, String path) {
        if (path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars/")
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
        if ((HttpMethod.POST.equals(method) && "/api/products".equals(path))
                || (HttpMethod.PUT.equals(method) && path.matches("^/api/products/[^/]+/stock$"))
                || (HttpMethod.POST.equals(method) && "/api/seckill/activities".equals(path))
                || (HttpMethod.GET.equals(method) && path.startsWith("/api/orders/admin"))) {
            return Role.ADMIN;
        }
        return Role.CUSTOMER;
    }
}
