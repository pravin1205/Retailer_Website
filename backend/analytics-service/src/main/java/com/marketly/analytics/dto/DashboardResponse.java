package com.marketly.analytics.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DashboardResponse {

    private BigDecimal revenueToday;
    private BigDecimal revenueLast7Days;
    private BigDecimal revenueLast30Days;

    private long ordersToday;
    private long ordersLast7Days;
    private long ordersLast30Days;

    private BigDecimal averageOrderValue;

    private long cancelledOrdersToday;

    /** Daily revenue series for charts — last 30 days */
    private List<DailyStat> dailyRevenue;

    @Data
    @Builder
    public static class DailyStat {
        private String date;          // "2026-06-16"
        private BigDecimal revenue;
        private long orders;
    }
}
