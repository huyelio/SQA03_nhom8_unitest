package com.example.AuthService.service.impl;

import com.example.AuthService.dto.stats.RevenueStatsFilter;
import com.example.AuthService.dto.stats.RevenueSummaryDto;
import com.example.AuthService.dto.stats.RevenueTimeSeriesDto;
import com.example.AuthService.dto.stats.TopProductDto;
import com.example.AuthService.enums.StatsGroupBy;
import com.example.AuthService.enums.StatsMode;
import com.example.AuthService.repository.OrderRepository;
import com.example.AuthService.repository.PaymentRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit test {@link AdminStatisticsServiceImpl}. Toàn bộ JPQL đi qua {@link EntityManager} — mock typed query,
 * tương đương kiểm tra “đọc DB” mà không cần MySQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminStatisticsServiceImpl")
@SuppressWarnings("unchecked")
class AdminStatisticsServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private AdminStatisticsServiceImpl adminStatisticsService;

    @BeforeEach
    void stubEntityManagerQueries() {
        TypedQuery<BigDecimal> bigDecimalTypedQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(BigDecimal.class))).thenReturn(bigDecimalTypedQuery);
        when(bigDecimalTypedQuery.setParameter(anyString(), any())).thenReturn(bigDecimalTypedQuery);
        when(bigDecimalTypedQuery.getSingleResult()).thenReturn(BigDecimal.ZERO);

        TypedQuery<Long> longTypedQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Long.class))).thenReturn(longTypedQuery);
        when(longTypedQuery.setParameter(anyString(), any())).thenReturn(longTypedQuery);
        when(longTypedQuery.getSingleResult()).thenReturn(0L);

        TypedQuery<Object[]> objectArrayTypedQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(Object[].class))).thenReturn(objectArrayTypedQuery);
        when(objectArrayTypedQuery.setParameter(anyString(), any())).thenReturn(objectArrayTypedQuery);
        when(objectArrayTypedQuery.setMaxResults(anyInt())).thenReturn(objectArrayTypedQuery);
        when(objectArrayTypedQuery.getResultList()).thenReturn(java.util.Collections.emptyList());
    }

    /** Test Case ID: TC-ADMIN-STATS-SUMMARY-01 */
    @Test
    @DisplayName("getRevenueSummary returns DTO with zero metrics when queries return zero")
    void getRevenueSummaryReturnsDto() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(1));
        filter.setTo(LocalDateTime.now());
        filter.setMode(StatsMode.CASHFLOW_VNPAY_ONLY);

        RevenueSummaryDto dto = adminStatisticsService.getRevenueSummary(filter);

        assertNotNull(dto);
        assertEquals(BigDecimal.ZERO, dto.getNetRevenue());
    }

    /** Test Case ID: TC-ADMIN-STATS-SERIES-01 */
    @Test
    @DisplayName("getRevenueTimeSeries returns empty points when no daily rows")
    void getRevenueTimeSeriesReturnsStructure() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(2));
        filter.setTo(LocalDateTime.now());
        filter.setGroupBy(StatsGroupBy.DAY);
        filter.setMode(StatsMode.SALES_ALL);

        RevenueTimeSeriesDto dto = adminStatisticsService.getRevenueTimeSeries(filter);

        assertNotNull(dto);
        assertEquals(StatsGroupBy.DAY, dto.getGroupBy());
        assertNotNull(dto.getPoints());
    }

    /** Test Case ID: TC-ADMIN-STATS-TOP-01 */
    @Test
    @DisplayName("getTopProducts returns page (possibly empty)")
    void getTopProductsReturnsPage() {
        RevenueStatsFilter filter = new RevenueStatsFilter();
        filter.setFrom(LocalDateTime.now().minusDays(7));
        filter.setTo(LocalDateTime.now());
        filter.setTopN(5);
        filter.setMode(StatsMode.SALES_ALL);

        Page<TopProductDto> page = adminStatisticsService.getTopProducts(filter);

        assertNotNull(page);
        assertTrue(page.getContent().isEmpty());
    }
}
