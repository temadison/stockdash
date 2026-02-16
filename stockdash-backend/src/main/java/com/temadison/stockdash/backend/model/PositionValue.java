package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;

public record PositionValue(String symbol, BigDecimal marketValue) {
}
