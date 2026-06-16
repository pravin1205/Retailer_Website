package com.marketly.customer.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.PageResponse;
import com.marketly.customer.dto.AddressRequest;
import com.marketly.customer.dto.CustomerRequest;
import com.marketly.customer.entity.Address;
import com.marketly.customer.entity.Customer;
import com.marketly.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Customer profiles, addresses, loyalty")
public class CustomerController {

    private final CustomerService customerService;

    // ── My profile (customer self-service) ────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get my customer profile for this tenant")
    public ResponseEntity<ApiResponse<Customer>> getMyProfile(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        Customer customer = customerService.getByUserAndTenant(
            UUID.fromString(userId), UUID.fromString(tenantId));
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    @PatchMapping("/me")
    @Operation(summary = "Update my profile")
    public ResponseEntity<ApiResponse<Customer>> updateMyProfile(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody CustomerRequest req) {
        Customer customer = customerService.updateProfile(
            UUID.fromString(userId), UUID.fromString(tenantId), req);
        return ResponseEntity.ok(ApiResponse.success(customer));
    }

    // ── Addresses ─────────────────────────────────────────────────────────

    @GetMapping("/me/addresses")
    @Operation(summary = "List my saved addresses")
    public ResponseEntity<ApiResponse<List<Address>>> listAddresses(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<Address> addresses = customerService.listAddresses(
            UUID.fromString(userId), UUID.fromString(tenantId));
        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PostMapping("/me/addresses")
    @Operation(summary = "Add a new address")
    public ResponseEntity<ApiResponse<Address>> addAddress(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @Valid @RequestBody AddressRequest req) {
        Address address = customerService.addAddress(
            UUID.fromString(userId), UUID.fromString(tenantId), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(address));
    }

    @PutMapping("/me/addresses/{addressId}")
    @Operation(summary = "Update an address")
    public ResponseEntity<ApiResponse<Address>> updateAddress(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest req) {
        Address address = customerService.updateAddress(
            UUID.fromString(userId), UUID.fromString(tenantId), addressId, req);
        return ResponseEntity.ok(ApiResponse.success(address));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    @Operation(summary = "Remove an address")
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader("X-User-ID")   String userId,
            @RequestHeader("X-Tenant-ID") String tenantId,
            @PathVariable UUID addressId) {
        customerService.deleteAddress(
            UUID.fromString(userId), UUID.fromString(tenantId), addressId);
        return ResponseEntity.noContent().build();
    }

    // ── Admin / store-owner views ─────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all customers for this tenant (owner/manager view)")
    public ResponseEntity<ApiResponse<PageResponse<Customer>>> listCustomers(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<Customer> result = customerService.listCustomers(
            UUID.fromString(tenantId), search, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
