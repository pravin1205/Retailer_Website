package com.marketly.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {

    @NotBlank(message = "Product name is required")
    private String name;

    private String brand;
    private String description;
    private UUID categoryId;
    private String sku;
    private String barcode;
    private String unit;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    private BigDecimal price;

    private BigDecimal mrp;
    private BigDecimal costPrice;
    private BigDecimal taxRate;
    private String[] tags;
    private Integer initialStock;
    private Integer lowStockThreshold;
}
