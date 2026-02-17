package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;

public record StockPerformanceValue(String symbol, BigDecimal marketValue) {
}
