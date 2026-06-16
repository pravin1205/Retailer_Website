package com.marketly.order.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.order.dto.AddToCartRequest;
import com.marketly.order.entity.Cart;
import com.marketly.order.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "Get active cart")
    public ResponseEntity<ApiResponse<Cart>> getCart(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        Cart cart = cartService.getOrCreateCart(
            UUID.fromString(tenantId), UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/items")
    @Operation(summary = "Add item to cart")
    public ResponseEntity<ApiResponse<Cart>> addItem(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody AddToCartRequest req) {
        Cart cart = cartService.addItem(
            UUID.fromString(tenantId), UUID.fromString(userId), req);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PatchMapping("/items/{itemId}")
    @Operation(summary = "Update item quantity (set to 0 to remove)")
    public ResponseEntity<ApiResponse<Cart>> updateQuantity(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID itemId,
            @RequestBody Map<String, Integer> body) {
        Cart cart = cartService.updateItemQuantity(
            UUID.fromString(tenantId), UUID.fromString(userId),
            itemId, body.getOrDefault("quantity", 0));
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Remove item from cart")
    public ResponseEntity<ApiResponse<Cart>> removeItem(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID itemId) {
        Cart cart = cartService.removeItem(
            UUID.fromString(tenantId), UUID.fromString(userId), itemId);
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @PostMapping("/coupon")
    @Operation(summary = "Apply coupon to cart")
    public ResponseEntity<ApiResponse<Cart>> applyCoupon(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, String> body) {
        Cart cart = cartService.applyCoupon(
            UUID.fromString(tenantId), UUID.fromString(userId),
            body.get("code"));
        return ResponseEntity.ok(ApiResponse.success(cart));
    }

    @DeleteMapping("/coupon")
    @Operation(summary = "Remove coupon from cart")
    public ResponseEntity<ApiResponse<Cart>> removeCoupon(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        Cart cart = cartService.removeCoupon(
            UUID.fromString(tenantId), UUID.fromString(userId));
        return ResponseEntity.ok(ApiResponse.success(cart));
    }
}
