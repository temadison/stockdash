package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;

public record PositionValueDto(
        String symbol,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal marketValue
) {
}
