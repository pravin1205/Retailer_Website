package com.marketly.order.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.PageResponse;
import com.marketly.order.dto.CheckoutRequest;
import com.marketly.order.entity.Order;
import com.marketly.order.service.CheckoutService;
import com.marketly.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle — checkout, tracking, status updates")
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService    orderService;

    @PostMapping
    @Operation(summary = "Checkout — create order from cart")
    public ResponseEntity<ApiResponse<Order>> checkout(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody CheckoutRequest req,
            @RequestBody(required = false) Map<String, Object> addressSnapshot) {
        // In production, address snapshot would be fetched from customer-service
        // via Feign using req.getAddressId(). For Phase 2 we accept it in request body.
        Order order = checkoutService.checkout(
            UUID.fromString(tenantId), UUID.fromString(userId),
            req, addressSnapshot != null ? addressSnapshot : Map.of());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(order));
    }

    @GetMapping
    @Operation(summary = "List orders (customer sees own; owner/manager sees all)")
    public ResponseEntity<ApiResponse<PageResponse<Order>>> listOrders(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Roles")     String roles,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {

        boolean isStoreAdmin = roles != null &&
            (roles.contains("TENANT_OWNER") || roles.contains("STORE_MANAGER")
             || roles.contains("STORE_STAFF") || roles.contains("SUPER_ADMIN"));

        PageResponse<Order> result;
        if (isStoreAdmin) {
            result = orderService.listOrdersForTenant(
                UUID.fromString(tenantId), status, search, page, size);
        } else {
            result = orderService.listOrdersForCustomer(
                UUID.fromString(tenantId), UUID.fromString(userId), page, size);
        }
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<ApiResponse<Order>> getOrder(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID orderId) {
        Order order = orderService.getOrder(orderId, UUID.fromString(tenantId));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PatchMapping("/{orderId}/status")
    @Operation(summary = "Update order status (store side)")
    public ResponseEntity<ApiResponse<Order>> updateStatus(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        Order order = orderService.updateStatus(
            orderId, UUID.fromString(tenantId),
            body.get("status"), body.get("note"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "Customer cancels their order")
    public ResponseEntity<ApiResponse<Order>> cancelOrder(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID orderId,
            @RequestBody Map<String, String> body) {
        Order order = orderService.cancelOrder(
            orderId, UUID.fromString(tenantId),
            UUID.fromString(userId), body.get("reason"));
        return ResponseEntity.ok(ApiResponse.success(order));
    }
}
