package com.marketly.customer.exception;

import com.marketly.common.dto.ApiResponse;
import com.marketly.common.dto.ErrorResponse;
import com.marketly.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, WebRequest req) {
        ErrorResponse error = ErrorResponse.builder()
            .code(ex.getErrorCode()).message(ex.getMessage())
            .timestamp(Instant.now())
            .path(req.getDescription(false).replace("uri=", "")).build();
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiResponse.error(error));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> ErrorResponse.FieldError.builder()
                .field(fe.getField()).message(fe.getDefaultMessage()).build())
            .toList();
        ErrorResponse error = ErrorResponse.builder()
            .code("VALIDATION_FAILED").message("Request validation failed.")
            .details(details).timestamp(Instant.now()).build();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiResponse.error(error));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error in customer-service: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error(ErrorResponse.builder()
                .code("INTERNAL_ERROR").message("An unexpected error occurred.")
                .timestamp(Instant.now()).build()));
    }
}
