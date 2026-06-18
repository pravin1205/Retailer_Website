package com.marketly.identity.controller;

import com.marketly.common.dto.ApiResponse;
import com.marketly.identity.dto.OtpRequest;
import com.marketly.identity.dto.OtpVerifyRequest;
import com.marketly.identity.dto.OtpVerifyResponse;
import com.marketly.identity.event.UserEventProducer;
import com.marketly.identity.service.OtpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/otp")
@RequiredArgsConstructor
@Tag(name = "OTP Authentication", description = "Mobile OTP send and verify for customer and seller onboarding")
public class OtpController {

    private final OtpService        otpService;
    private final UserEventProducer userEventProducer;

    @PostMapping("/send")
    @Operation(summary = "Send OTP to a mobile number")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendOtp(
            @Valid @RequestBody OtpRequest request) {

        String otpPlain = otpService.generateAndStore(request.getPhone());

        // Publish Kafka event → notification-service sends the OTP
        userEventProducer.publishOtpRequested(request.getPhone(), otpPlain);

        Map<String, Object> data = Map.of(
            "message",          "OTP sent successfully",
            "expiresInSeconds",  600
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/verify")
    @Operation(summary = "Verify OTP and receive JWT tokens")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(
            @Valid @RequestBody OtpVerifyRequest request) {

        OtpVerifyResponse response = otpService.verifyAndIssueToken(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
