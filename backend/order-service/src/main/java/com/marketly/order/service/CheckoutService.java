package com.marketly.order.service;

import com.marketly.common.exception.BusinessException;
import com.marketly.common.exception.ResourceNotFoundException;
import com.marketly.order.dto.CheckoutRequest;
import com.marketly.order.entity.*;
import com.marketly.order.event.OrderEventProducer;
import com.marketly.order.repository.CartRepository;
import com.marketly.order.repository.CouponRepository;
import com.marketly.order.repository.OrderRepository;
import com.marketly.order.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private static final BigDecimal DELIVERY_CHARGE = new BigDecimal("30.00");
    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("499.00");
    private static final int POINTS_PER_RUPEE = 10; // 1 point per ₹10

    private final CartRepository    cartRepository;
    private final OrderRepository   orderRepository;
    private final PaymentRepository paymentRepository;
    private final CouponRepository  couponRepository;
    private final CartService       cartService;
    private final OrderEventProducer orderEventProducer;
    private final ObjectMapper      objectMapper;

    /**
     * Core checkout flow:
     * 1. Validate cart is not empty
     * 2. Resolve delivery address (from customer-service payload in request)
     * 3. Calculate subtotal, discount, delivery, tax, total
     * 4. Create Order + OrderItems
     * 5. Create Payment record (PENDING)
     * 6. Mark cart CONVERTED
     * 7. Publish OrderCreatedEvent
     */
    @Transactional
    public Order checkout(UUID tenantId, UUID customerId,
                          CheckoutRequest req, Map<String, Object> addressSnapshot) {
        // 1. Load and validate cart
        Cart cart = cartRepository.findById(req.getCartId())
            .orElseThrow(() -> new ResourceNotFoundException("Cart", req.getCartId().toString()));

        if (!cart.getTenantId().equals(tenantId)) {
            throw new BusinessException("CART_NOT_FOUND",
                "Cart not found.", HttpStatus.NOT_FOUND);
        }
        if (cart.isEmpty()) {
            throw new BusinessException("EMPTY_CART",
                "Cannot checkout an empty cart.", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!"ACTIVE".equals(cart.getStatus())) {
            throw new BusinessException("CART_ALREADY_USED",
                "This cart has already been checked out.", HttpStatus.CONFLICT);
        }

        // 2. Calculate totals
        BigDecimal subtotal = cart.getItems().stream()
            .map(CartItem::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        if (cart.getCouponCode() != null) {
            discount = resolveCouponDiscount(tenantId, cart.getCouponCode(), subtotal);
        }

        BigDecimal delivery = subtotal.subtract(discount)
            .compareTo(FREE_DELIVERY_THRESHOLD) >= 0
            ? BigDecimal.ZERO : DELIVERY_CHARGE;

        if ("COD".equals(req.getPaymentMethod())) {
            delivery = delivery.add(new BigDecimal("20.00")); // COD handling charge
        }

        BigDecimal total = subtotal.subtract(discount).add(delivery);

        // 3. Generate human-readable order number
        String orderNumber = generateOrderNumber(tenantId);

        // 4. Serialize address snapshot
        String addressJson;
        try {
            addressJson = objectMapper.writeValueAsString(addressSnapshot);
        } catch (JsonProcessingException e) {
            addressJson = "{}";
        }

        // 5. Build Order
        Order order = Order.builder()
            .customerId(customerId)
            .orderNumber(orderNumber)
            .status("PLACED")
            .subtotal(subtotal)
            .discountAmount(discount)
            .deliveryCharge(delivery)
            .taxAmount(BigDecimal.ZERO)
            .totalAmount(total)
            .couponCode(cart.getCouponCode())
            .deliverySlot(req.getDeliverySlot())
            .deliveryAddress(addressJson)
            .paymentMethod(req.getPaymentMethod())
            .notes(req.getNotes())
            .build();
        order.setTenantId(tenantId);

        // 6. Build OrderItems from CartItems (snapshot)
        cart.getItems().forEach(cartItem -> {
            OrderItem oi = OrderItem.builder()
                .order(order)
                .productId(cartItem.getProductId())
                .variantId(cartItem.getVariantId())
                .productName(cartItem.getProductName())
                .unitPrice(cartItem.getUnitPrice())
                .quantity(cartItem.getQuantity())
                .lineTotal(cartItem.getLineTotal())
                .imageUrl(cartItem.getImageUrl())
                .build();
            oi.setTenantId(tenantId);
            order.getItems().add(oi);
        });

        Order savedOrder = orderRepository.save(order);

        // 7. Create initial Payment record
        Payment payment = Payment.builder()
            .order(savedOrder)
            .amount(total)
            .method(req.getPaymentMethod())
            .status("COD".equals(req.getPaymentMethod()) ? "PENDING_COD" : "PENDING")
            .build();
        payment.setTenantId(tenantId);
        paymentRepository.save(payment);

        // 8. Mark coupon used
        if (cart.getCouponCode() != null) {
            incrementCouponUsage(tenantId, cart.getCouponCode());
        }

        // 9. Convert cart
        cartService.markConverted(cart.getId());

        // 10. Publish event (async — Kafka)
        orderEventProducer.publishOrderCreated(savedOrder);

        log.info("Order {} created for customer {} (tenant {})",
                 orderNumber, customerId, tenantId);
        return savedOrder;
    }

    private BigDecimal resolveCouponDiscount(UUID tenantId, String code, BigDecimal subtotal) {
        return couponRepository
            .findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
            .filter(Coupon::isCurrentlyValid)
            .map(c -> c.calculateDiscount(subtotal))
            .orElse(BigDecimal.ZERO);
    }

    private void incrementCouponUsage(UUID tenantId, String code) {
        couponRepository.findByTenantIdAndCodeAndDeletedAtIsNull(tenantId, code)
            .ifPresent(c -> {
                c.setUsedCount(c.getUsedCount() + 1);
                couponRepository.save(c);
            });
    }

    private String generateOrderNumber(UUID tenantId) {
        // Format: {TENANT_PREFIX}-{YYYYMMDD}-{SEQ4}
        // e.g.  : MKT-20260616-0042
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix  = "MKT-" + dateStr;
        int seq = orderRepository.findMaxSequenceForPrefix(tenantId, prefix) + 1;
        return String.format("%s-%04d", prefix, seq);
    }
}
