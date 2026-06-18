package com.marketly.product.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.marketly.common.dto.ApiResponse;
import com.marketly.common.exception.BusinessException;
import com.marketly.common.exception.DuplicateResourceException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.common.tenant.TenantContext;
import com.marketly.product.entity.Category;
import com.marketly.product.repository.CategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management per tenant")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    // ── Request DTO ───────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CategoryRequest {
        @NotBlank @Size(max = 200)
        private String name;

        @Size(max = 200)
        private String slug;

        private String description;
        private String imageUrl;
        private int    sortOrder = 0;
        private UUID   parentId;
    }

    // ── Response helper ───────────────────────────────────────────────────────

    private Map<String, Object> toDto(Category c) {
        return Map.of(
            "id",          c.getId(),
            "name",        c.getName(),
            "slug",        c.getSlug(),
            "description", c.getDescription() != null ? c.getDescription() : "",
            "imageUrl",    c.getImageUrl()    != null ? c.getImageUrl()    : "",
            "sortOrder",   c.getSortOrder(),
            "isActive",    c.isActive(),
            "parentId",    c.getParent() != null ? c.getParent().getId() : ""
        );
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List root categories for the current tenant")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> listCategories(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantIdHeader) {
        if (!StringUtils.hasText(tenantIdHeader)) {
            throw new BusinessException("MISSING_TENANT", "X-Tenant-ID header is required.", HttpStatus.BAD_REQUEST);
        }
        UUID tenantId = UUID.fromString(tenantIdHeader);
        List<Map<String, Object>> result = categoryRepository
            .findByTenantIdAndParentIsNullAndDeletedAtIsNull(tenantId)
            .stream()
            .map(this::toDto)
            .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping
    @Operation(summary = "Create a category for the current tenant")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCategory(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @Valid @RequestBody CategoryRequest request) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        TenantContext.set(tenantId);
        try {
            String slug = request.getSlug() != null && !request.getSlug().isBlank()
                ? request.getSlug()
                : request.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");

            if (categoryRepository.existsBySlugAndTenantIdAndDeletedAtIsNull(slug, tenantId)) {
                throw new DuplicateResourceException("Category with slug '" + slug + "' already exists.");
            }

            Category parent = null;
            if (request.getParentId() != null) {
                parent = categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.getParentId(), tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Category", request.getParentId().toString()));
            }

            Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder())
                .parent(parent)
                .active(true)
                .build();

            category = categoryRepository.save(category);
            return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(toDto(category)));
        } finally {
            TenantContext.clear();
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateCategory(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @PathVariable UUID id,
            @RequestBody CategoryRequest request) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        Category category = categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id.toString()));

        if (request.getName() != null)        category.setName(request.getName());
        if (request.getDescription() != null) category.setDescription(request.getDescription());
        if (request.getImageUrl() != null)    category.setImageUrl(request.getImageUrl());
        category.setSortOrder(request.getSortOrder());
        category = categoryRepository.save(category);
        return ResponseEntity.ok(ApiResponse.success(toDto(category)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a category")
    @Transactional
    public ResponseEntity<Void> deleteCategory(
            @RequestHeader("X-Tenant-ID") String tenantIdHeader,
            @PathVariable UUID id) {
        UUID tenantId = UUID.fromString(tenantIdHeader);
        Category category = categoryRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Category", id.toString()));
        category.softDelete();
        categoryRepository.save(category);
        return ResponseEntity.noContent().build();
    }
}
