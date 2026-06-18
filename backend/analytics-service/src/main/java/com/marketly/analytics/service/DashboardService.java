package com.marketly.analytics.service;

import com.marketly.analytics.dto.DashboardResponse;
import com.marketly.analytics.entity.OrderSummary;
import com.marketly.analytics.repository.OrderSummaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final OrderSummaryRepository summaryRepository;

    /**
     * Builds the full dashboard for a tenant.
     * Cached for 5 minutes — acceptable lag for analytics.
     */
    @Cacheable(value = "analytics-dashboard", key = "#a0.toString()")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(UUID tenantId) {
        LocalDate today  = LocalDate.now();
        LocalDate day7   = today.minusDays(6);
        LocalDate day30  = today.minusDays(29);

        // Today
        OrderSummary todaySummary = summaryRepository
            .findByTenantIdAndSummaryDate(tenantId, today)
            .orElse(emptyDay(tenantId, today));

        // Aggregates
        BigDecimal rev7  = summaryRepository.sumRevenue(tenantId, day7, today);
        BigDecimal rev30 = summaryRepository.sumRevenue(tenantId, day30, today);
        long ord7        = summaryRepository.sumOrders(tenantId, day7, today);
        long ord30       = summaryRepository.sumOrders(tenantId, day30, today);

        // Daily series for chart (last 30 days)
        List<OrderSummary> series = summaryRepository
            .findByTenantIdAndSummaryDateBetweenOrderBySummaryDateAsc(tenantId, day30, today);

        List<DashboardResponse.DailyStat> dailyStats = series.stream()
            .map(s -> DashboardResponse.DailyStat.builder()
                .date(s.getSummaryDate().toString())
                .revenue(s.getGrossRevenue())
                .orders(s.getTotalOrders())
                .build())
            .toList();

        return DashboardResponse.builder()
            .revenueToday(todaySummary.getGrossRevenue())
            .revenueLast7Days(rev7)
            .revenueLast30Days(rev30)
            .ordersToday(todaySummary.getTotalOrders())
            .ordersLast7Days(ord7)
            .ordersLast30Days(ord30)
            .averageOrderValue(ord30 == 0 ? BigDecimal.ZERO
                : rev30.divide(BigDecimal.valueOf(ord30), 2, java.math.RoundingMode.HALF_UP))
            .cancelledOrdersToday(todaySummary.getCancelledOrders())
            .dailyRevenue(dailyStats)
            .build();
    }

    // ── Called by Kafka consumer ──────────────────────────────────────────

    @Transactional
    public void recordOrder(UUID tenantId, BigDecimal total,
                            BigDecimal discount, boolean isNewCustomer) {
        LocalDate today = LocalDate.now();
        OrderSummary summary = summaryRepository
            .findByTenantIdAndSummaryDate(tenantId, today)
            .orElseGet(() -> {
                OrderSummary s = emptyDay(tenantId, today);
                return summaryRepository.save(s);
            });

        summary.setTotalOrders(summary.getTotalOrders() + 1);
        summary.setGrossRevenue(summary.getGrossRevenue().add(total));
        summary.setNetRevenue(summary.getNetRevenue().add(total.subtract(discount)));
        summary.setTotalDiscount(summary.getTotalDiscount().add(discount));
        if (isNewCustomer) summary.setNewCustomers(summary.getNewCustomers() + 1);
        summary.setUpdatedAt(java.time.Instant.now());
        summaryRepository.save(summary);
    }

    @Transactional
    public void recordCancellation(UUID tenantId, BigDecimal total) {
        LocalDate today = LocalDate.now();
        summaryRepository.findByTenantIdAndSummaryDate(tenantId, today)
            .ifPresent(summary -> {
                summary.setCancelledOrders(summary.getCancelledOrders() + 1);
                // Reverse revenue
                summary.setGrossRevenue(summary.getGrossRevenue().subtract(total));
                summary.setUpdatedAt(java.time.Instant.now());
                summaryRepository.save(summary);
            });
    }

    private OrderSummary emptyDay(UUID tenantId, LocalDate date) {
        return OrderSummary.builder()
            .tenantId(tenantId)
            .summaryDate(date)
            .build();
    }
}
