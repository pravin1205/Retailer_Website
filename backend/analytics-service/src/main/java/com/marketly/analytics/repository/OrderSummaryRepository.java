package com.marketly.analytics.repository;

import com.marketly.analytics.entity.OrderSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderSummaryRepository extends JpaRepository<OrderSummary, UUID> {

    Optional<OrderSummary> findByTenantIdAndSummaryDate(UUID tenantId, LocalDate date);

    List<OrderSummary> findByTenantIdAndSummaryDateBetweenOrderBySummaryDateAsc(
        UUID tenantId, LocalDate from, LocalDate to);

    @Query("SELECT COALESCE(SUM(s.grossRevenue), 0) FROM OrderSummary s " +
           "WHERE s.tenantId = :tenantId AND s.summaryDate BETWEEN :from AND :to")
    java.math.BigDecimal sumRevenue(
        @Param("tenantId") UUID tenantId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );

    @Query("SELECT COALESCE(SUM(s.totalOrders), 0) FROM OrderSummary s " +
           "WHERE s.tenantId = :tenantId AND s.summaryDate BETWEEN :from AND :to")
    long sumOrders(
        @Param("tenantId") UUID tenantId,
        @Param("from") LocalDate from,
        @Param("to") LocalDate to
    );
}
