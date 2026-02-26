package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioPerformancePointDto(
        LocalDate date,
        BigDecimal totalValue,
        List<StockPerformanceValueDto> stocks
) {
}
