package com.marketly.identity.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.identity.dto.LoginRequest;
import com.marketly.identity.dto.LoginResponse;
import com.marketly.identity.dto.RegisterRequest;
import com.marketly.identity.entity.User;
import com.marketly.identity.service.AuditLogService;
import com.marketly.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register, login, token management")
public class AuthController {

    private final AuthService     authService;
    private final AuditLogService auditLogService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        User user = authService.register(request);
        auditLogService.log(AuditLogService.REGISTER,
            user.getId(), null, http.getRemoteAddr(), http.getHeader("User-Agent"));
        Map<String, Object> data = Map.of(
            "userId",  user.getId(),
            "email",   user.getEmail(),
            "message", "Registration successful. Verification email sent."
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive JWT tokens")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {
        try {
            LoginResponse response = authService.login(request);
            auditLogService.log(AuditLogService.LOGIN_SUCCESS,
                response.getUser().getId(), response.getUser().getTenantId(),
                http.getRemoteAddr(), http.getHeader("User-Agent"));
            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (Exception e) {
            auditLogService.log(AuditLogService.LOGIN_FAILED,
                null, null, http.getRemoteAddr(), http.getHeader("User-Agent"));
            throw e;
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh token for new access + refresh token pair")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            @RequestBody Map<String, String> body) {
        LoginResponse response = authService.refreshToken(body.get("refreshToken"));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token")
    public ResponseEntity<Void> logout(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            HttpServletRequest http) {
        authService.logout(body.get("refreshToken"));
        if (userId != null) {
            auditLogService.log(AuditLogService.LOGOUT,
                UUID.fromString(userId), null,
                http.getRemoteAddr(), http.getHeader("User-Agent"));
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    @Operation(summary = "Change password for the authenticated user")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("X-User-ID") String userId,
            @RequestBody Map<String, String> body,
            HttpServletRequest http) {
        UUID uid = UUID.fromString(userId);
        authService.changePassword(uid, body.get("currentPassword"), body.get("newPassword"));
        auditLogService.log(AuditLogService.PASSWORD_CHANGED,
            uid, null, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.noContent().build();
    }
}
