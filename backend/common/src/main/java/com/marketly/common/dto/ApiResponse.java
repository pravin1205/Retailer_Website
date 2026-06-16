package com.marketly.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard API response envelope.
 * Every endpoint returns this wrapper — success or error.
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;
    private final Meta meta;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
        this.meta = new Meta();
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }

    @Getter
    public static class Meta {
        private final Instant timestamp = Instant.now();
        private final String requestId = UUID.randomUUID().toString();
        private final String version = "v1";
    }
}
