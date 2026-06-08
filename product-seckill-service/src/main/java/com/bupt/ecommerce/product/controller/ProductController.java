package com.bupt.ecommerce.product.controller;

import com.bupt.ecommerce.common.api.ApiResponse;
import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.common.security.AuthHeaders;
import com.bupt.ecommerce.common.security.Role;
import com.bupt.ecommerce.product.dto.CreateProductRequest;
import com.bupt.ecommerce.product.dto.ProductResponse;
import com.bupt.ecommerce.product.dto.SetStockRequest;
import com.bupt.ecommerce.product.dto.StockResponse;
import com.bupt.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@Tag(name = "02 商品与库存", description = "商品查询、创建和库存配置")
public class ProductController {

    private final ProductService productService;
    private final Long defaultAdminId;
    private final boolean enableDefaultUser;

    public ProductController(
            ProductService productService,
            @Value("${app.local-demo.default-admin-id}") Long defaultAdminId,
            @Value("${app.local-demo.enable-default-user:false}") boolean enableDefaultUser
    ) {
        this.productService = productService;
        this.defaultAdminId = defaultAdminId;
        this.enableDefaultUser = enableDefaultUser;
    }

    @GetMapping
    @Operation(summary = "商品分页查询", description = "PUBLIC 接口，分页返回商品列表")
    public ApiResponse<PageResponse<ProductResponse>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "10") int pageSize,
            @RequestParam(value = "keyword", required = false) String keyword
    ) {
        return ApiResponse.success(productService.page(page, pageSize, keyword));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "商品详情", description = "PUBLIC 接口，按商品 ID 查询详情")
    public ApiResponse<ProductResponse> detail(@PathVariable("productId") Long productId) {
        return ApiResponse.success(productService.detail(productId));
    }

    @PutMapping("/{productId}/stock")
    @Operation(summary = "设置库存", description = "ADMIN 接口，设置指定商品库存")
    public ApiResponse<StockResponse> stock(
            @PathVariable("productId") Long productId,
            @Valid @RequestBody SetStockRequest request,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(productService.setStock(productId, request));
    }

    @PostMapping
    @Operation(summary = "创建商品", description = "ADMIN 接口，创建商品基础信息")
    public ApiResponse<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            @RequestHeader(value = AuthHeaders.USER_ID, required = false) Long userId,
            @RequestHeader(value = AuthHeaders.ROLE, required = false) String role
    ) {
        requireAdmin(role);
        return ApiResponse.success(productService.create(request, resolveUserId(userId, defaultAdminId)));
    }

    private void requireAdmin(String role) {
        if (enableDefaultUser && role == null) {
            return;
        }
        if (Role.ADMIN.name().equals(role)) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    private Long resolveUserId(Long userId, Long defaultId) {
        if (userId != null) {
            return userId;
        }
        if (enableDefaultUser) {
            return defaultId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
