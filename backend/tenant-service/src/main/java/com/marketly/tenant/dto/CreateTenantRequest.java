package com.marketly.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateTenantRequest {

    @NotBlank(message = "Slug is required")
    @Pattern(regexp = "^[a-z0-9-]{3,100}$",
             message = "Slug must be 3-100 lowercase letters, numbers, or hyphens")
    private String slug;

    @NotBlank(message = "Name is required")
    @Size(max = 200)
    private String name;

    @Size(max = 500)
    private String tagline;

    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 100)
    private String category;

    @NotBlank(message = "Owner email is required")
    private String ownerEmail;

    private String subscriptionPlan = "FREE";

    private String accentColor;
    private String logoUrl;
}
