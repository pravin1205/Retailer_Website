package com.marketly.common.exception;

import org.springframework.http.HttpStatus;

public class TenantNotFoundException extends BusinessException {
    public TenantNotFoundException(String slug) {
        super("TENANT_NOT_FOUND",
              "Tenant '" + slug + "' not found or is not active.",
              HttpStatus.NOT_FOUND);
    }
}
