package com.bupt.ecommerce.product.service;

import com.bupt.ecommerce.common.api.ErrorCode;
import com.bupt.ecommerce.common.api.PageResponse;
import com.bupt.ecommerce.common.exception.BusinessException;
import com.bupt.ecommerce.product.dto.CreateProductRequest;
import com.bupt.ecommerce.product.dto.ProductResponse;
import com.bupt.ecommerce.product.dto.SetStockRequest;
import com.bupt.ecommerce.product.dto.StockResponse;
import com.bupt.ecommerce.product.entity.Product;
import com.bupt.ecommerce.product.entity.ProductStatus;
import com.bupt.ecommerce.product.entity.ProductStock;
import com.bupt.ecommerce.product.repository.ProductRepository;
import com.bupt.ecommerce.product.repository.ProductStockRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductStockRepository stockRepository;

    public ProductService(ProductRepository productRepository, ProductStockRepository stockRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStatus(ProductStatus.ON_SALE);
        product.setCreatedBy(operatorId);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        Product saved = productRepository.save(product);
        ProductStock stock = new ProductStock();
        stock.setProductId(saved.getId());
        stock.setTotalStock(0);
        stock.setAvailableStock(0);
        stock.setReservedStock(0);
        stock.setVersion(0L);
        stock.setUpdatedAt(now);
        return ProductResponse.from(saved, stockRepository.save(stock));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> page(int page, int pageSize, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 1) - 1, Math.max(pageSize, 1));
        Page<Product> result;
        if (StringUtils.hasText(keyword)) {
            result = productRepository.findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
                    keyword,
                    keyword,
                    pageable
            );
        } else {
            result = productRepository.findAll(pageable);
        }
        return new PageResponse<>(
                result.getContent().stream()
                        .map(product -> ProductResponse.from(
                                product,
                                stockRepository.findByProductId(product.getId()).orElse(null)
                        ))
                        .toList(),
                page,
                pageSize,
                result.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public ProductResponse detail(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ProductStock stock = stockRepository.findByProductId(productId).orElse(null);
        return ProductResponse.from(product, stock);
    }

    @Transactional
    public StockResponse setStock(Long productId, SetStockRequest request) {
        productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        ProductStock stock = stockRepository.findByProductIdForUpdate(productId).orElseGet(ProductStock::new);
        int reservedStock = stock.getReservedStock() == null ? 0 : stock.getReservedStock();
        if (request.stock() < reservedStock) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        stock.setProductId(productId);
        stock.setTotalStock(request.stock());
        stock.setAvailableStock(request.stock() - reservedStock);
        stock.setReservedStock(reservedStock);
        stock.setVersion(stock.getVersion() == null ? 0L : stock.getVersion() + 1);
        stock.setUpdatedAt(LocalDateTime.now());
        return StockResponse.from(stockRepository.save(stock));
    }
}
