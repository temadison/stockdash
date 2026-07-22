package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;

public record PositionValue(
        String symbol,
        BigDecimal quantity,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal costBasis,
        BigDecimal gainLoss,
        BigDecimal totalReturn,
        BigDecimal cagr
) {
    public PositionValue(String symbol, BigDecimal quantity, BigDecimal currentPrice, BigDecimal marketValue) {
        this(symbol, quantity, currentPrice, marketValue, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
    }
}
