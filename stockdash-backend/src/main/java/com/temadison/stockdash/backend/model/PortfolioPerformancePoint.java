package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformancePoint(
        LocalDate date,
        BigDecimal totalValue,
        BigDecimal netAmountSpent,
        List<StockPerformanceValue> stocks
) {
}
