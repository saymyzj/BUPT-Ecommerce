package com.bupt.ecommerce.gateway.filter;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.JwtTokenUtil;
import com.bupt.ecommerce.common.security.RequestUser;
import com.bupt.ecommerce.common.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Component
public class BasicGatewayFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(BasicGatewayFilter.class);
    private final GatewayAuthPolicy authPolicy;
    private final ObjectMapper objectMapper;
    private final String jwtSecret;

    public BasicGatewayFilter(ObjectMapper objectMapper,
                              @Value("${app.jwt.secret}") String jwtSecret) {
        this.authPolicy = new GatewayAuthPolicy();
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        HttpMethod method = request.getMethod();
        log.info("gateway request {} {}", method, request.getURI());

        if (HttpMethod.OPTIONS.equals(method) || authPolicy.isPublicEndpoint(method, path)) {
            return chain.filter(exchange);
        }

        String token = JwtTokenUtil.extractBearerToken(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }

        RequestUser requestUser;
        try {
            requestUser = JwtTokenUtil.parseToken(token, jwtSecret);
        } catch (RuntimeException ex) {
            log.warn("gateway token rejected for {} {}: {}", method, path, ex.getMessage());
            return writeError(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED);
        }

        Role requiredRole = authPolicy.requiredRole(method, path);
        if (requiredRole == Role.ADMIN && requestUser.role() != Role.ADMIN) {
            return writeError(exchange, HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }

        ServerHttpRequest authorizedRequest = request.mutate()
                .headers(headers -> {
                    headers.set(AuthHeaders.USER_ID, String.valueOf(requestUser.userId()));
                    headers.set(AuthHeaders.USERNAME, requestUser.username());
                    headers.set(AuthHeaders.ROLE, requestUser.role().name());
                })
                .build();
        return chain.filter(exchange.mutate().request(authorizedRequest).build());
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] body = objectMapper.writeValueAsString(
                    ApiResponse.failure(errorCode.code(), errorCode.message())
            ).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception ex) {
            byte[] body = "{\"code\":500,\"message\":\"服务器内部错误\",\"data\":null}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
