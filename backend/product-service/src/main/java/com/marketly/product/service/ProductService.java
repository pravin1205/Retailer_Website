package com.marketly.product.service;

import com.marketly.common.dto.PageResponse;
import com.marketly.common.exception.DuplicateResourceException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.common.tenant.TenantContext;
import com.marketly.product.dto.CreateProductRequest;
import com.marketly.product.entity.Category;
import com.marketly.product.entity.Inventory;
import com.marketly.product.entity.Product;
import com.marketly.product.event.ProductEventProducer;
import com.marketly.product.repository.CategoryRepository;
import com.marketly.product.repository.InventoryRepository;
import com.marketly.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductEventProducer productEventProducer;

    @Transactional
    @CacheEvict(value = "product-list", allEntries = true)
    public Product createProduct(CreateProductRequest request) {
        UUID tenantId = TenantContext.get();

        if (request.getSku() != null &&
            productRepository.existsBySkuAndTenantIdAndDeletedAtIsNull(request.getSku(), tenantId)) {
            throw new DuplicateResourceException(
                "Product with SKU '" + request.getSku() + "' already exists in this store.");
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(
                    request.getCategoryId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.getCategoryId().toString()));
        }

        Product product = Product.builder()
            .name(request.getName())
            .brand(request.getBrand())
            .description(request.getDescription())
            .category(category)
            .sku(request.getSku())
            .barcode(request.getBarcode())
            .unit(request.getUnit())
            .price(request.getPrice())
            .mrp(request.getMrp())
            .costPrice(request.getCostPrice())
            .taxRate(request.getTaxRate() != null ? request.getTaxRate() : java.math.BigDecimal.ZERO)
            .tags(request.getTags())
            .active(true)
            .featured(false)
            .build();

        product = productRepository.save(product);

        // Create initial inventory record
        Inventory inventory = Inventory.builder()
            .product(product)
            .quantityOnHand(request.getInitialStock() != null ? request.getInitialStock() : 0)
            .lowStockThreshold(request.getLowStockThreshold() != null ? request.getLowStockThreshold() : 5)
            .build();
        inventoryRepository.save(inventory);

        productEventProducer.publishProductCreated(product, inventory);
        log.info("Product created: {} (tenant: {})", product.getId(), tenantId);
        return product;
    }

    @Cacheable(value = "product-detail", key = "#a0.toString() + ':' + #a1.toString()")
    @Transactional(readOnly = true)
    public Product getProduct(UUID id, UUID tenantId) {
        return productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
    }

    @Transactional(readOnly = true)
    public PageResponse<Product> listProducts(UUID tenantId, String search, UUID categoryId,
                                              Boolean isActive, Boolean isFeatured,
                                              int page, int size) {
        PageRequest pageable = PageRequest.of(
            page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result = productRepository.findByFilters(
            tenantId, search, categoryId, isActive, isFeatured, pageable);
        return PageResponse.of(result);
    }

    @CacheEvict(value = {"product-detail", "product-list"}, allEntries = true)
    @Transactional
    public Product updateProduct(UUID id, UUID tenantId, CreateProductRequest request) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));

        if (request.getName() != null)     product.setName(request.getName());
        if (request.getBrand() != null)    product.setBrand(request.getBrand());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null)    product.setPrice(request.getPrice());
        if (request.getMrp() != null)      product.setMrp(request.getMrp());
        if (request.getTags() != null)     product.setTags(request.getTags());

        return productRepository.save(product);
    }

    @CacheEvict(value = {"product-detail", "product-list"}, allEntries = true)
    @Transactional
    public void deleteProduct(UUID id, UUID tenantId) {
        Product product = productRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Product", id.toString()));
        product.softDelete();
        productRepository.save(product);
    }
}
