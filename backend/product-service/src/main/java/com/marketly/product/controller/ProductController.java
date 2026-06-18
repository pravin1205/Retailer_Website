package com.marketly.product.controller;

import com.marketly.common.exception.BusinessException;
import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.PageResponse;
import com.marketly.common.tenant.TenantContext;
import com.marketly.product.dto.CreateProductRequest;
import com.marketly.product.entity.Product;
import com.marketly.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product catalog management")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "List products for the current tenant")
    public ResponseEntity<ApiResponse<PageResponse<Product>>> listProducts(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) Boolean isFeatured,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!StringUtils.hasText(tenantIdHeader)) {
            throw new BusinessException("MISSING_TENANT", "X-Tenant-ID header is required.", HttpStatus.BAD_REQUEST);
        }
        UUID tenantId = UUID.fromString(tenantIdHeader);
        TenantContext.set(tenantId);
        try {
            PageResponse<Product> result = productService.listProducts(
                tenantId, search, categoryId, isActive, isFeatured, page, size);
            return ResponseEntity.ok(ApiResponse.success(result));
        } finally {
            TenantContext.clear();
        }
    }

    @PostMapping
    @Operation(summary = "Create a new product")
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @Valid @RequestBody CreateProductRequest request) {
        TenantContext.set(UUID.fromString(tenantIdHeader));
        try {
            Product product = productService.createProduct(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(product));
        } finally {
            TenantContext.clear();
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ApiResponse<Product>> getProduct(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @PathVariable UUID id) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        Product product = productService.getProduct(id, tenantId);
        return ResponseEntity.ok(ApiResponse.success(product));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update product")
    public ResponseEntity<ApiResponse<Product>> updateProduct(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @PathVariable UUID id,
            @Valid @RequestBody CreateProductRequest request) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        TenantContext.set(tenantId);
        try {
            Product product = productService.updateProduct(id, tenantId, request);
            return ResponseEntity.ok(ApiResponse.success(product));
        } finally {
            TenantContext.clear();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a product")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @PathVariable UUID id) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        productService.deleteProduct(id, tenantId);
        return ResponseEntity.noContent().build();
    }
}
