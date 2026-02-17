package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;

public record PositionValue(String symbol, long quantity, BigDecimal currentPrice, BigDecimal marketValue) {
}
