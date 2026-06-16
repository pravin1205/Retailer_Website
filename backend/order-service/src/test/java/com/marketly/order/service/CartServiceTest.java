package com.marketly.order.service;

import com.marketly.common.exception.BusinessException;
import com.marketly.order.client.ProductServiceClient;
import com.marketly.order.dto.AddToCartRequest;
import com.marketly.order.entity.Cart;
import com.marketly.order.repository.CartRepository;
import com.marketly.order.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock CartRepository       cartRepository;
    @Mock CouponRepository     couponRepository;
    @Mock ProductServiceClient productServiceClient;

    @InjectMocks CartService cartService;

    UUID tenantId   = UUID.randomUUID();
    UUID customerId = UUID.randomUUID();
    UUID productId  = UUID.randomUUID();

    // Helpers

    private ProductServiceClient.ProductResponse inStockResponse(int qty) {
        var inv = new ProductServiceClient.ProductResponse.ProductData.InventoryData();
        inv.setInStock(true);
        inv.setQuantityAvailable(qty);

        var data = new ProductServiceClient.ProductResponse.ProductData();
        data.setId(productId);
        data.setName("Organic Apples");
        data.setPrice(new BigDecimal("129.00"));
        data.setInventory(inv);

        var resp = new ProductServiceClient.ProductResponse();
        resp.setSuccess(true);
        resp.setData(data);
        return resp;
    }

    private ProductServiceClient.ProductResponse outOfStockResponse() {
        var inv = new ProductServiceClient.ProductResponse.ProductData.InventoryData();
        inv.setInStock(false);
        inv.setQuantityAvailable(0);

        var data = new ProductServiceClient.ProductResponse.ProductData();
        data.setId(productId);
        data.setName("Organic Apples");
        data.setPrice(new BigDecimal("129.00"));
        data.setInventory(inv);

        var resp = new ProductServiceClient.ProductResponse();
        resp.setSuccess(true);
        resp.setData(data);
        return resp;
    }

    private Cart emptyCart() {
        Cart cart = new Cart();
        cart.setTenantId(tenantId);
        cart.setCustomerId(customerId);
        return cart;
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem: in-stock product → cart contains one item")
    void addItem_inStock_addsItemToCart() {
        when(productServiceClient.getProduct(productId, tenantId.toString()))
            .thenReturn(inStockResponse(10));
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
            .thenReturn(Optional.of(emptyCart()));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(productId);
        req.setQuantity(2);

        Cart result = cartService.addItem(tenantId, customerId, req);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(result.getItems().get(0).getUnitPrice()).isEqualByComparingTo("129.00");
    }

    @Test
    @DisplayName("addItem: out-of-stock product → throws BusinessException OUT_OF_STOCK")
    void addItem_outOfStock_throwsException() {
        when(productServiceClient.getProduct(productId, tenantId.toString()))
            .thenReturn(outOfStockResponse());

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(productId);
        req.setQuantity(1);

        assertThatThrownBy(() -> cartService.addItem(tenantId, customerId, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("out of stock");
    }

    @Test
    @DisplayName("addItem: quantity exceeds available stock → throws INSUFFICIENT_STOCK")
    void addItem_exceedsStock_throwsException() {
        when(productServiceClient.getProduct(productId, tenantId.toString()))
            .thenReturn(inStockResponse(3));
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
            .thenReturn(Optional.of(emptyCart()));

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(productId);
        req.setQuantity(10);  // 10 > 3 available

        assertThatThrownBy(() -> cartService.addItem(tenantId, customerId, req))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Only 3 units available");
    }

    @Test
    @DisplayName("addItem: same product twice → quantities are merged")
    void addItem_sameProductTwice_mergesQuantity() {
        when(productServiceClient.getProduct(productId, tenantId.toString()))
            .thenReturn(inStockResponse(20));

        Cart existingCart = emptyCart();
        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
            .thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AddToCartRequest req = new AddToCartRequest();
        req.setProductId(productId);
        req.setQuantity(3);

        // First add
        cartService.addItem(tenantId, customerId, req);
        // Second add — cart already has 3, adding 3 more
        Cart result = cartService.addItem(tenantId, customerId, req);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("removeItem: removes item from cart")
    void removeItem_removesItem() {
        Cart cart = emptyCart();
        // Add a fake item directly
        var item = new com.marketly.order.entity.CartItem();
        item.setId(UUID.randomUUID());
        item.setProductId(productId);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.TEN);
        item.setProductName("Test");
        cart.getItems().add(item);

        UUID itemId = item.getId();

        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.removeItem(tenantId, customerId, itemId);

        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("updateItemQuantity: setting quantity to 0 removes item")
    void updateItemQuantity_zero_removesItem() {
        Cart cart = emptyCart();
        var item = new com.marketly.order.entity.CartItem();
        item.setId(UUID.randomUUID());
        item.setProductId(productId);
        item.setQuantity(2);
        item.setUnitPrice(BigDecimal.TEN);
        item.setProductName("Test");
        cart.getItems().add(item);

        when(cartRepository.findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE"))
            .thenReturn(Optional.of(cart));
        when(cartRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Cart result = cartService.updateItemQuantity(tenantId, customerId, item.getId(), 0);

        assertThat(result.getItems()).isEmpty();
    }
}
