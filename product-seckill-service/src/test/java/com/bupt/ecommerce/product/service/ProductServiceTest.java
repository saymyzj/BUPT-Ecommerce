package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.product.dto.CreateProductRequest;
import com.bupt.ecommerce.product.dto.ProductResponse;
import com.bupt.ecommerce.product.entity.Product;
import com.bupt.ecommerce.product.entity.ProductStock;
import com.bupt.ecommerce.product.repository.ProductRepository;
import com.bupt.ecommerce.product.repository.ProductStockRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Test
    void createShouldInitializeZeroStock() {
        ProductRepository productRepository = mock(ProductRepository.class);
        ProductStockRepository stockRepository = mock(ProductStockRepository.class);
        ProductService productService = new ProductService(productRepository, stockRepository);

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(20001L);
            return product;
        });
        when(stockRepository.save(any(ProductStock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(
                new CreateProductRequest("phone", new BigDecimal("1999.00"), "demo"),
                10001L
        );

        assertNotNull(response.stock());
        assertEquals(20001L, response.stock().productId());
        assertEquals(0, response.stock().totalStock());
        assertEquals(0, response.stock().availableStock());
        assertEquals(0, response.stock().reservedStock());
    }
}
