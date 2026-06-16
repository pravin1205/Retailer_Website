package com.marketly.order.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign client for synchronous calls to product-service.
 * Used during checkout to verify stock and get current price.
 */
@FeignClient(name = "product-service", path = "/api/v1")
public interface ProductServiceClient {

    @GetMapping("/products/{id}")
    ProductResponse getProduct(
        @PathVariable("id") UUID id,
        @RequestHeader("X-Tenant-ID") String tenantId
    );

    /** Minimal product data needed by order-service */
    @Data
    class ProductResponse {
        private boolean success;
        private ProductData data;

        @Data
        public static class ProductData {
            private UUID id;
            private String name;
            private String sku;
            private BigDecimal price;
            private String unit;
            private String imageUrl;
            private InventoryData inventory;

            @Data
            public static class InventoryData {
                private int quantityAvailable;
                private boolean inStock;
            }
        }
    }
}
