package com.temadison.stockdash.backend.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyClosePricePointDto(LocalDate date, BigDecimal closePrice) {
}
