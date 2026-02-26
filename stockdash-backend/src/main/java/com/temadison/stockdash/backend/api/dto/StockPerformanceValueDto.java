package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;

public record StockPerformanceValueDto(
        String symbol,
        BigDecimal marketValue
) {
}
