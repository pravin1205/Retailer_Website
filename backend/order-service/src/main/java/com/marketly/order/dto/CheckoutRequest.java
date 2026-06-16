package com.marketly.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CheckoutRequest {

    @NotNull(message = "Cart ID is required")
    private UUID cartId;

    @NotNull(message = "Address ID is required")
    private UUID addressId;

    private String deliverySlot;

    @NotBlank(message = "Payment method is required")
    private String paymentMethod;  // COD | UPI | CARD | WALLET

    private String notes;
}
