package com.marketly.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class KycRequest {

    @NotBlank(message = "Aadhaar number is required")
    @Pattern(regexp = "^\\d{4}\\s?\\d{4}\\s?\\d{4}$",
             message = "Aadhaar must be 12 digits (with optional spaces)")
    private String aadhaarNumber;

    @NotBlank(message = "PAN number is required")
    @Pattern(regexp = "^[A-Z]{5}[0-9]{4}[A-Z]$",
             message = "PAN must be in format AAAAA9999A")
    private String panNumber;

    /** Optional — required only for GST-applicable business categories */
    @Pattern(regexp = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][0-9][Z][A-Z0-9]$",
             message = "Invalid GST number format")
    private String gstNumber;

    /** URLs of uploaded KYC documents (Aadhaar front+back, PAN) */
    private List<String> documentUrls;

    /** Optional store photo */
    private String storeImageUrl;

    /** Admin review notes (populated on PATCH /approve, not on POST /kyc) */
    @Size(max = 1000)
    private String reviewNotes;
}
