package com.marketly.analytics.controller;

import com.marketly.analytics.dto.DashboardResponse;
import com.marketly.analytics.entity.OrderSummary;
import com.marketly.analytics.repository.OrderSummaryRepository;
import com.marketly.analytics.service.DashboardService;
import com.marketly.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Sales dashboard, KPIs, and reporting")
public class AnalyticsController {

    private final DashboardService       dashboardService;
    private final OrderSummaryRepository summaryRepository;

    @GetMapping("/dashboard")
    @Operation(summary = "Get KPI dashboard for the current tenant")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        DashboardResponse dashboard = dashboardService
            .getDashboard(UUID.fromString(tenantId));
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    @GetMapping("/revenue")
    @Operation(summary = "Get daily revenue series for a date range")
    public ResponseEntity<ApiResponse<List<OrderSummary>>> getRevenueSeries(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<OrderSummary> series = summaryRepository
            .findByTenantIdAndSummaryDateBetweenOrderBySummaryDateAsc(
                UUID.fromString(tenantId), from, to);
        return ResponseEntity.ok(ApiResponse.success(series));
    }
}
