package com.marketly.order.service;

import com.marketly.common.exception.BusinessException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.order.client.ProductServiceClient;
import com.marketly.order.dto.AddToCartRequest;
import com.marketly.order.entity.Cart;
import com.marketly.order.entity.CartItem;
import com.marketly.order.entity.Coupon;
import com.marketly.order.repository.CartRepository;
import com.marketly.order.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final CouponRepository couponRepository;
    private final ProductServiceClient productServiceClient;

    @Transactional
    public Cart getOrCreateCart(UUID tenantId, UUID customerId) {
        return cartRepository
            .findByTenantIdAndCustomerIdAndStatus(tenantId, customerId, "ACTIVE")
            .orElseGet(() -> {
                Cart cart = Cart.builder().customerId(customerId).build();
                cart.setTenantId(tenantId);
                return cartRepository.save(cart);
            });
    }

    @Transactional
    public Cart addItem(UUID tenantId, UUID customerId, AddToCartRequest req) {
        // Verify product exists and has stock
        var productResponse = productServiceClient.getProduct(
            req.getProductId(), tenantId.toString());

        if (productResponse.getData() == null) {
            throw new ResourceNotFoundException("Product", req.getProductId().toString());
        }

        var productData = productResponse.getData();
        var inventory = productData.getInventory();

        if (inventory == null || !inventory.isInStock()) {
            throw new BusinessException("OUT_OF_STOCK",
                "Product '" + productData.getName() + "' is out of stock.",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (req.getQuantity() > inventory.getQuantityAvailable()) {
            throw new BusinessException("INSUFFICIENT_STOCK",
                "Only " + inventory.getQuantityAvailable() + " units available.",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Cart cart = getOrCreateCart(tenantId, customerId);

        // If item already exists, increment quantity
        CartItem existing = cart.getItems().stream()
            .filter(i -> i.getProductId().equals(req.getProductId())
                && java.util.Objects.equals(i.getVariantId(), req.getVariantId()))
            .findFirst()
            .orElse(null);

        if (existing != null) {
            int newQty = existing.getQuantity() + req.getQuantity();
            if (newQty > inventory.getQuantityAvailable()) {
                throw new BusinessException("INSUFFICIENT_STOCK",
                    "Cannot add more — only " + inventory.getQuantityAvailable() + " units available.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
            }
            existing.setQuantity(newQty);
        } else {
            CartItem item = CartItem.builder()
                .cart(cart)
                .productId(req.getProductId())
                .variantId(req.getVariantId())
                .productName(productData.getName())
                .unitPrice(productData.getPrice())
                .imageUrl(productData.getImageUrl())
                .quantity(req.getQuantity())
                .build();
            item.setTenantId(tenantId);
            cart.getItems().add(item);
        }

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateItemQuantity(UUID tenantId, UUID customerId,
                                   UUID itemId, int quantity) {
        Cart cart = getOrCreateCart(tenantId, customerId);
        CartItem item = cart.getItems().stream()
            .filter(i -> i.getId().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Cart item", itemId.toString()));

        if (quantity <= 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(quantity);
        }
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeItem(UUID tenantId, UUID customerId, UUID itemId) {
        Cart cart = getOrCreateCart(tenantId, customerId);
        cart.getItems().removeIf(i -> i.getId().equals(itemId));
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart applyCoupon(UUID tenantId, UUID customerId, String code) {
        Cart cart = getOrCreateCart(tenantId, customerId);
        if (cart.isEmpty()) {
            throw new BusinessException("EMPTY_CART",
                "Cannot apply coupon to an empty cart.", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        Coupon coupon = couponRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code.toUpperCase())
            .orElseThrow(() -> new BusinessException("INVALID_COUPON",
                "Coupon '" + code + "' is not valid.", HttpStatus.UNPROCESSABLE_ENTITY));

        if (!coupon.isCurrentlyValid()) {
            throw new BusinessException("COUPON_EXPIRED",
                "This coupon has expired or reached its usage limit.",
                HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (coupon.getMinOrderValue() != null) {
            java.math.BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getLineTotal)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            if (subtotal.compareTo(coupon.getMinOrderValue()) < 0) {
                throw new BusinessException("COUPON_MIN_ORDER",
                    "Minimum order value of ₹" + coupon.getMinOrderValue() + " required.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        cart.setCouponCode(code.toUpperCase());
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart removeCoupon(UUID tenantId, UUID customerId) {
        Cart cart = getOrCreateCart(tenantId, customerId);
        cart.setCouponCode(null);
        return cartRepository.save(cart);
    }

    @Transactional
    public void markConverted(UUID cartId) {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cart.setStatus("CONVERTED");
            cartRepository.save(cart);
        });
    }
}
