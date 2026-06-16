package com.marketly.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @Size(max = 50)
    private String label;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 300)
    private String line1;

    @Size(max = 300)
    private String line2;

    @NotBlank(message = "City is required")
    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String state;

    @NotBlank(message = "Pincode is required")
    @Size(max = 20)
    private String pincode;

    private boolean isDefault;
}
