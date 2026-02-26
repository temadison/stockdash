package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;

public record PositionValueDto(
        String symbol,
        long quantity,
        BigDecimal currentPrice,
        BigDecimal marketValue
) {
}
