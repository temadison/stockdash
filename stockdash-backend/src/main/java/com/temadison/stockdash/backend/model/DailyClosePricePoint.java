package com.temadison.stockdash.backend.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyClosePricePoint(LocalDate date, BigDecimal closePrice) {
}
